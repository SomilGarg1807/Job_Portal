(() => {
    'use strict';
    let activeClose = () => {};
    document.querySelectorAll('input[list]').forEach((input, index) => {
        if (input.disabled || input.readOnly || input.dataset.suggestions) return;
        const source = document.getElementById(input.getAttribute('list'));
        if (!source) return;
        input.removeAttribute('list');
        const panel = document.createElement('div'); panel.className = 'portal-options'; panel.hidden = true;
        const list = document.createElement('div'); list.id = `portal-options-${index}`; list.setAttribute('role', 'listbox');
        const caption = document.createElement('div'); caption.className = 'options-caption'; caption.textContent = 'Suggestions';
        const status = document.createElement('div'); status.className = 'options-message'; status.setAttribute('role', 'status');
        panel.append(caption, list, status); document.body.append(panel);
        input.setAttribute('role', 'combobox'); input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-controls', list.id); input.setAttribute('aria-expanded', 'false'); input.autocomplete = 'off';
        let selected = -1, choices = [];
        const close = () => { panel.hidden = true; input.setAttribute('aria-expanded', 'false'); input.removeAttribute('aria-activedescendant'); };
        const place = () => {
            const box = input.getBoundingClientRect();
            const below = innerHeight - box.bottom - 12, above = box.top - 12;
            const upward = below < 150 && above > below;
            panel.style.maxHeight = Math.max(90, Math.min(280, upward ? above : below)) + 'px';
            panel.style.width = Math.min(Math.max(box.width, 220), innerWidth - 24) + 'px';
            panel.style.left = Math.max(12, Math.min(box.left, innerWidth - parseFloat(panel.style.width) - 12)) + 'px';
            panel.style.top = upward ? 'auto' : box.bottom + 6 + 'px';
            panel.style.bottom = upward ? innerHeight - box.top + 6 + 'px' : 'auto';
        };
        const choose = value => {
            input.value = value; close(); input.focus();
            input.dispatchEvent(new Event('input', {bubbles:true}));
            input.dispatchEvent(new Event('change', {bubbles:true})); close();
        };
        const render = () => {
            if (document.activeElement !== input) return;
            activeClose(); activeClose = close;
            const query = input.value.trim().toLocaleLowerCase();
            const all = [...new Set([...source.options].filter(o => !o.disabled).map(o => o.value))];
            choices = all.filter(v => v.toLocaleLowerCase().includes(query)).slice(0, 40);
            selected = -1; list.replaceChildren();
            choices.forEach((value, i) => {
                const option = document.createElement('div'); option.setAttribute('role', 'option'); option.id = `${list.id}-${i}`;
                option.setAttribute('aria-selected', 'false'); option.textContent = value;
                option.addEventListener('pointerdown', event => event.preventDefault());
                option.addEventListener('click', () => choose(value)); list.append(option);
            });
            status.textContent = choices.length ? 'Choose a suggestion or keep your own text.' : 'No suggestions yet. You can type your own value.';
            panel.hidden = false; place(); input.setAttribute('aria-expanded', 'true');
        };
        input.addEventListener('focus', render); input.addEventListener('input', render); input.addEventListener('blur', close);
        input.addEventListener('keydown', event => {
            if (event.key === 'Escape') { close(); return; }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault(); if (panel.hidden) render(); if (!choices.length) return;
                selected = (selected + (event.key === 'ArrowDown' ? 1 : -1) + choices.length) % choices.length;
                [...list.children].forEach((o,i) => o.setAttribute('aria-selected', String(i === selected)));
                input.setAttribute('aria-activedescendant', list.children[selected].id);
                list.children[selected].scrollIntoView({block:'nearest'});
            } else if (event.key === 'Enter' && !panel.hidden && selected >= 0) { event.preventDefault(); choose(choices[selected]); }
        });
        new MutationObserver(() => { if (!panel.hidden) render(); }).observe(source, {childList:true, subtree:true});
        window.addEventListener('resize', () => { if (!panel.hidden) place(); });
        window.addEventListener('scroll', event => {
            if (panel.hidden || panel.contains(event.target)) return;
            const box = input.getBoundingClientRect();
            if (box.bottom < 0 || box.top > innerHeight) close(); else place();
        }, true);
    });
})();
