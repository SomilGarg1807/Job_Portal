(() => {
    'use strict';
    const form = document.querySelector('#ai-form');
    if (!form) return;
    const status = document.querySelector('#ai-status');
    const result = document.querySelector('#ai-result');
    const copy = document.querySelector('#ai-copy');
    const use = document.querySelector('#ai-use');
    let feedback = document.querySelector('#copy-feedback');
    if (!feedback) {
        feedback = document.createElement('p');
        feedback.id = 'copy-feedback';
        feedback.className = 'copy-feedback';
        feedback.setAttribute('role', 'status');
        feedback.setAttribute('aria-live', 'polite');
        copy.after(feedback);
    }
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = form.querySelector('[type=submit]');
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 40000);
        button.disabled = true;
        result.hidden = copy.hidden = true;
        if (use) use.hidden = true;
        feedback.textContent = '';
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
            if (!data.text?.trim()) throw new Error('No draft was returned. Please try again.');
            result.textContent = data.text;
            result.hidden = copy.hidden = false;
            if (use) use.hidden = false;
            status.textContent = 'Your draft is ready. Review and adapt it before using.';
        } catch (error) {
            status.textContent = error.name === 'AbortError' ? 'This is taking longer than expected. Please try again shortly.' : error.message;
        } finally {
            clearTimeout(timeout);
            button.disabled = false;
            form.removeAttribute('aria-busy');
        }
    });
    copy.addEventListener('click', () => window.Portal.copyText(result.textContent, copy, feedback, result));
})();
