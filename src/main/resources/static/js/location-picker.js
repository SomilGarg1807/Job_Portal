(function () {
    const STATES_URL = 'https://countriesnow.space/api/v0.1/countries/states';
    const CITIES_URL = 'https://countriesnow.space/api/v0.1/countries/state/cities';
    const CACHE_KEY = 'hotdevjobs-location-data-v1';

    let locationDataPromise;

    function setOptions(datalist, values) {
        if (!datalist) return;
        datalist.replaceChildren(...values.map(value => {
            const option = document.createElement('option');
            option.value = value;
            return option;
        }));
    }

    function findCountry(data, countryName) {
        const normalized = countryName.trim().toLowerCase();
        return data.find(country => country.name.toLowerCase() === normalized);
    }

    async function getLocationData() {
        if (locationDataPromise) return locationDataPromise;

        locationDataPromise = (async function () {
            try {
                const cached = JSON.parse(sessionStorage.getItem(CACHE_KEY) || 'null');
                if (Array.isArray(cached)) return cached;
            } catch (_) { /* Suggestions also work when storage is unavailable. */ }

            const response = await fetch(STATES_URL, {signal: AbortSignal.timeout(8000)});
            if (!response.ok) throw new Error('Unable to load countries');
            const payload = await response.json();
            if (payload.error || !Array.isArray(payload.data)) throw new Error('Invalid location response');

            try { sessionStorage.setItem(CACHE_KEY, JSON.stringify(payload.data)); } catch (_) { }
            return payload.data;
        })().catch(error => { locationDataPromise = null; throw error; });

        return locationDataPromise;
    }

    async function loadCities(country, state) {
        const response = await fetch(CITIES_URL, {
            method: 'POST',
            signal: AbortSignal.timeout(8000),
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ country: country, state: state })
        });
        if (!response.ok) throw new Error('Unable to load cities');
        const payload = await response.json();
        if (payload.error || !Array.isArray(payload.data)) return [];
        return payload.data;
    }

    document.querySelectorAll('[data-location-picker]').forEach(async function (picker) {
        const countryInput = picker.querySelector('[data-location-country]');
        const stateInput = picker.querySelector('[data-location-state]');
        const cityInput = picker.querySelector('[data-location-city]');
        const countryOptions = picker.querySelector('[data-country-options]');
        const stateOptions = picker.querySelector('[data-state-options]');
        const cityOptions = picker.querySelector('[data-city-options]');
        const status = picker.querySelector('[data-location-status]');

        if (!countryInput || countryInput.disabled) return;
        let cityRequest = 0;

        function updateStatus(message, className) {
            if (!status) return;
            status.textContent = message;
            status.className = 'location-status full-width' + (className ? ' ' + className : '');
        }

        async function populateStates(clearDependentFields) {
            const data = await getLocationData();
            const country = findCountry(data, countryInput.value);
            const states = country && Array.isArray(country.states) ? country.states.map(item => item.name) : [];
            setOptions(stateOptions, states);
            if (clearDependentFields) {
                cityRequest++;
                stateInput.value = '';
                cityInput.value = '';
                setOptions(cityOptions, []);
            }
            updateStatus(country ? 'State suggestions are ready. You can also type manually.' : 'Choose a listed country or type the location manually.');
        }

        async function populateCities(clearCity) {
            const request = ++cityRequest;
            setOptions(cityOptions, []);
            if (clearCity) cityInput.value = '';
            if (!countryInput.value.trim() || !stateInput.value.trim()) return;
            const country = countryInput.value.trim(), state = stateInput.value.trim();
            updateStatus('Loading city suggestions…', 'loading');
            try {
                const cities = await loadCities(country, state);
                if (request !== cityRequest || countryInput.value.trim() !== country || stateInput.value.trim() !== state) return;
                setOptions(cityOptions, cities);
                updateStatus(cities.length ? 'City suggestions are ready. You can also type manually.' : 'No city list was returned; type your city manually.');
            } catch (error) {
                if (request !== cityRequest) return;
                updateStatus('City suggestions are temporarily unavailable; type your city manually.', 'error');
            }
        }

        try {
            updateStatus('Loading location suggestions…', 'loading');
            const data = await getLocationData();
            setOptions(countryOptions, data.map(country => country.name));
            await populateStates(false);
            if (stateInput.value) await populateCities(false);
        } catch (error) {
            updateStatus('Location suggestions are temporarily unavailable; all fields still accept manual entry.', 'error');
        }

        countryInput.addEventListener('change', function () {
            populateStates(true).catch(() => updateStatus('Type your state and city manually.', 'error'));
        });
        stateInput.addEventListener('change', function () {
            populateCities(true);
        });
    });
})();
