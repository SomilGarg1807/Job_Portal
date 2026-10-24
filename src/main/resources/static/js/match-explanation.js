(() => {
    'use strict';
    const form = document.querySelector('#match-form');
    if (!form) return;
    const status = document.querySelector('#match-status');
    const result = document.querySelector('#match-result');
    const quote = (label, text) => {
        const span = document.createElement('span');
        span.className = 'match-quote';
        span.textContent = `${label}: “${text}”`;
        return span;
    };
    const group = (title, points, withProfile) => {
        const section = document.createElement('div');
        section.className = 'match-result-group';
        const heading = document.createElement('h4');
        heading.textContent = title;
        const list = document.createElement('ul');
        points.forEach(p => {
            const item = document.createElement('li');
            item.textContent = p.point;
            if (withProfile) item.append(quote('Your profile', p.profileQuote));
            item.append(quote('This job', p.jobQuote));
            list.append(item);
        });
        section.append(heading, list);
        return section;
    };
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        const button = form.querySelector('[type=submit]');
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 40000);
        button.disabled = true;
        form.setAttribute('aria-busy', 'true');
        result.hidden = true;
        result.replaceChildren();
        status.textContent = 'Comparing your profile with this role…';
        try {
            const response = await fetch(form.action, { method: 'POST', credentials: 'same-origin',
                body: new URLSearchParams(new FormData(form)), signal: controller.signal });
            if (response.redirected || !response.headers.get('content-type')?.includes('application/json'))
                throw new Error('Your session may have expired. Refresh the page and sign in again.');
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || 'Unable to explain this match right now.');
            if (data.matches.length) result.append(group('Shown in your profile', data.matches, true));
            if (data.gaps.length) result.append(group('Not shown in your profile', data.gaps, false));
            result.hidden = false;
            status.textContent = data.dropped
                ? `${data.dropped} point(s) were removed because they failed the checks.`
                : 'Every point below quotes your profile or this job.';
        } catch (error) {
            status.textContent = error.name === 'AbortError' ? 'This is taking longer than expected. Please try again shortly.' : error.message;
        } finally {
            clearTimeout(timeout);
            button.disabled = false;
            form.removeAttribute('aria-busy');
        }
    });
})();
