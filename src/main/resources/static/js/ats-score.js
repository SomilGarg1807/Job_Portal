(() => {
    const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; };
    const chips = (title, items, cls, empty) => {
        const box = el('div', 'ats-keywords'); box.append(el('h3', null, title));
        const list = el('div', 'ats-chips ' + cls);
        if (!items.length) list.append(el('span', 'ats-empty', empty));
        items.forEach(item => list.append(el('span', null, item)));
        box.append(list); return box;
    };
    const render = (target, r) => {
        target.replaceChildren();
        const head = el('div', 'ats-head');
        const ring = el('div', 'ats-ring'); ring.style.setProperty('--score', r.score);
        ring.setAttribute('role', 'img'); ring.setAttribute('aria-label', `ATS score ${r.score} out of 100`);
        ring.append(el('strong', null, r.score), el('small', null, '/100'));
        const summary = el('div'); summary.append(el('p', 'ats-band', r.band), el('p', 'field-hint', r.note));
        head.append(ring, summary);
        const bars = el('div', 'ats-bars');
        [['Keyword match', r.keywordScore], ['Role alignment', r.roleScore], ['Experience fit', r.experienceScore], ['Resume structure', r.resumeScore]]
            .forEach(([label, value]) => {
                const row = el('div', 'ats-bar'); row.append(el('span', null, label), el('b', null, value + '%'));
                const track = el('i'); const fill = el('em'); fill.style.width = value + '%'; track.append(fill); row.append(track); bars.append(row);
            });
        target.append(head, bars,
            chips('Keywords found', r.matched, 'found', 'No job keywords found yet.'),
            chips('Keywords missing', r.missing, 'missing', 'Every detected keyword is covered.'));
        target.hidden = false;
    };
    document.querySelectorAll('[data-ats-url]').forEach(panel => {
        const button = panel.querySelector('[data-ats-run]');
        const status = panel.querySelector('[data-ats-status]');
        const result = panel.querySelector('[data-ats-result]');
        const run = async () => {
            button.disabled = true; status.textContent = 'Calculating ATS score...';
            try {
                const response = await fetch(panel.dataset.atsUrl, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
                if (response.redirected || !response.headers.get('content-type')?.includes('application/json'))
                    throw new Error('Your session may have expired. Refresh and sign in again.');
                if (!response.ok) throw new Error('Unable to calculate the ATS score right now.');
                render(result, await response.json());
                status.textContent = ''; button.textContent = 'Recalculate ATS score';
            } catch (error) { status.textContent = error.message; }
            finally { button.disabled = false; }
        };
        button.addEventListener('click', run);
    });
})();
