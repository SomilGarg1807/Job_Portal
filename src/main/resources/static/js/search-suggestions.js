(() => {
    document.querySelectorAll('[data-suggestions]').forEach((input, index) => {
        const host = input.parentElement;
        host.classList.add('suggestion-host');
        const panel = document.createElement('div');
        panel.className = 'suggestion-panel';
        panel.hidden = true;
        const list = document.createElement('div');
        list.id = `suggestions-${index}`;
        list.setAttribute('role', 'listbox');
        panel.append(list);
        const status = document.createElement('div');
        status.className = 'suggestion-status';
        status.setAttribute('role', 'status');
        panel.append(status);
        if (input.name === 'location') {
            const credit = document.createElement('small');
            credit.textContent = 'Locations: Open-Meteo / GeoNames';
            panel.append(credit);
        }
        host.append(panel);
        input.autocomplete = 'off';
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-controls', list.id);
        input.setAttribute('aria-expanded', 'false');
        let timer, controller, sequence = 0, selected = -1, choices = [];
        const close = () => {
            panel.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
        };
        const choose = index => {
            input.value = choices[index].value;
            sequence++;
            controller?.abort();
            close();
            input.focus();
        };
        input.addEventListener('input', () => {
            clearTimeout(timer);
            controller?.abort();
            const request = ++sequence;
            const query = input.value.trim();
            close();
            if (query.length < 2 || query.length > 100) return;
            timer = setTimeout(async () => {
                controller = new AbortController();
                try {
                    const url = new URL(input.dataset.suggestions, location.href);
                    url.searchParams.set('q', query);
                    const response = await fetch(url, {signal: controller.signal});
                    if (!response.ok) throw new Error('unavailable');
                    const data = await response.json();
                    if (request !== sequence || document.activeElement !== input) return;
                    choices = Array.isArray(data) ? data : [];
                    selected = -1;
                    list.replaceChildren();
                    choices.forEach((choice, i) => {
                        const option = document.createElement('div');
                        option.id = `${list.id}-${i}`;
                        option.setAttribute('role', 'option');
                        option.setAttribute('aria-selected', 'false');
                        const title = document.createElement('span');
                        title.textContent = choice.label;
                        const kind = document.createElement('small');
                        kind.textContent = choice.kind;
                        option.append(title, kind);
                        option.addEventListener('mousedown', event => event.preventDefault());
                        option.addEventListener('click', () => choose(i));
                        list.append(option);
                    });
                    status.textContent = choices.length ? `${choices.length} suggestions. Use arrow keys to choose.` : 'No suggestions. You can search with your own text.';
                    panel.hidden = false;
                    input.setAttribute('aria-expanded', 'true');
                } catch (error) {
                    if (error.name === 'AbortError' || request !== sequence || document.activeElement !== input) return;
                    list.replaceChildren();
                    choices = [];
                    status.textContent = 'Suggestions unavailable. You can still type and search.';
                    panel.hidden = false;
                    input.setAttribute('aria-expanded', 'true');
                }
            }, 280);
        });
        input.addEventListener('keydown', event => {
            if (event.key === 'Escape') { sequence++; controller?.abort(); close(); return; }
            if (panel.hidden || !choices.length) return;
            if (event.key === 'Enter' && selected >= 0) { event.preventDefault(); choose(selected); }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                selected = (selected + (event.key === 'ArrowDown' ? 1 : -1) + choices.length) % choices.length;
                [...list.children].forEach((option, i) => option.setAttribute('aria-selected', String(i === selected)));
                input.setAttribute('aria-activedescendant', list.children[selected].id);
                list.children[selected].scrollIntoView({block: 'nearest'});
            }
        });
        input.addEventListener('blur', () => setTimeout(close, 150));
    });
    document.querySelectorAll('details.filter-panel, details.filters').forEach(panel => {
        const count = panel.querySelectorAll('input:checked').length;
        const badge = document.createElement('span');
        badge.className = 'filter-count';
        badge.textContent = count ? ` (${count} active)` : '';
        panel.querySelector('summary').append(badge);
        if (matchMedia('(max-width: 850px)').matches) panel.open = false;
    });
    const dates = document.querySelectorAll('#filterForm input[name="today"], #filterForm input[name="days7"], #filterForm input[name="days30"]');
    dates.forEach(input => input.addEventListener('change', () => {
        if (input.checked) dates.forEach(other => { if (other !== input) other.checked = false; });
    }));
})();
