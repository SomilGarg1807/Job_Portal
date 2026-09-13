(() => {
    const form = document.querySelector('#comparison-form');
    if (!form) return;
    const status = document.querySelector('#comparison-status');
    const result = document.querySelector('#comparison-result');
    const copy = document.querySelector('#comparison-copy');
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true; form.setAttribute('aria-busy', 'true');
        result.hidden = copy.hidden = true; result.textContent = '';
        document.querySelector('#comparison-copy-feedback').textContent = '';
        status.textContent = 'Comparing your evidence with this role...';
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 45000);
        try {
            const response = await fetch(form.action, { method: 'POST', credentials: 'same-origin',
                body: new URLSearchParams(new FormData(form)), signal: controller.signal });
            if (response.redirected || !response.headers.get('content-type')?.includes('application/json'))
                throw new Error('Your session may have expired. Refresh and sign in again.');
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || 'Unable to compare right now. Try again later.');
            if (!data.text?.trim()) throw new Error('No comparison was returned. Please try again.');
            result.textContent = data.text; result.hidden = copy.hidden = false;
            status.textContent = 'Your comparison is ready. Review each suggestion against your actual experience.';
        } catch (error) {
            status.textContent = error.name === 'AbortError' ? 'This is taking longer than expected. Please try again shortly.' : error.message;
        } finally { clearTimeout(timeout); button.disabled = false; form.removeAttribute('aria-busy'); }
    });
    copy.addEventListener('click', () => window.Portal.copyText(result.textContent, copy,
        document.querySelector('#comparison-copy-feedback'), result));
})();
