(() => {
    'use strict';
    const search = document.querySelector('#search-form');
    search.addEventListener('submit', (event) => {
        // A tab supplies its own view. Search, sort and filters retain the current tab.
        const currentView = document.querySelector('#current-view');
        currentView.name = event.submitter?.name === 'view' ? '' : 'view';
    });
    document.querySelector('#sort').addEventListener('change', () => search.requestSubmit());
    document.querySelectorAll('.date-filter').forEach(input => {
        input.addEventListener('change', () => {
            if (input.checked) document.querySelectorAll('.date-filter').forEach(other => {
                if (other !== input) other.checked = false;
            });
        });
    });
    const form = document.querySelector('#ai-form');
    const status = document.querySelector('#ai-status');
    const result = document.querySelector('#ai-result');
    const copy = document.querySelector('#ai-copy');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const button = form.querySelector('button');
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 40000);
        button.disabled = true;
        result.hidden = true;
        copy.hidden = true;
        status.textContent = 'Putting together your draft…';
        form.setAttribute('aria-busy', 'true');
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'HotDevJobs' },
                body: JSON.stringify({ context: form.elements.context.value, consent: document.querySelector('#ai-consent').checked }),
                signal: controller.signal
            });
            if (response.redirected || !response.headers.get('content-type')?.includes('application/json')) {
                throw new Error('Your session may have expired. Refresh the page and sign in again.');
            }
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || 'The assistant is unavailable. Please try again later.');
            result.textContent = data.text;
            result.hidden = false;
            copy.hidden = false;
            status.textContent = 'Your draft is ready. Review and adapt it before using.';
        } catch (error) {
            status.textContent = error.name === 'AbortError' ? 'This is taking longer than expected. Please try again shortly.' : error.message;
        } finally {
            clearTimeout(timeout);
            button.disabled = false;
            form.removeAttribute('aria-busy');
        }
    });
    copy.addEventListener('click', async () => {
        try {
            await navigator.clipboard.writeText(result.textContent);
            status.textContent = 'Copied to clipboard.';
        } catch (_) {
            status.textContent = 'Copy is unavailable in this browser. Select the result text to copy it.';
        }
    });
})();
