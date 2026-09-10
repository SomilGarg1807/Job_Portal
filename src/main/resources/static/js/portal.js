(() => {
    'use strict';
    document.querySelectorAll('[data-avatar]').forEach(avatar => {
        const picture = avatar.querySelector('img');
        const fallback = avatar.querySelector('[data-avatar-initial]');
        if (!picture) return;
        const showInitial = () => { picture.hidden = true; fallback.hidden = false; };
        picture.addEventListener('error', showInitial);
        if (picture.complete && picture.naturalWidth === 0) showInitial();
    });
    async function copyText(text, button, feedback, source) {
        if (!text?.trim()) { feedback.textContent = 'Generate a result first.'; return false; }
        let copied = false;
        const original = button.textContent;
        button.textContent = 'Copying…';
        try {
            if (navigator.clipboard?.writeText && window.isSecureContext) {
                await Promise.race([
                    navigator.clipboard.writeText(text),
                    new Promise((_, reject) => setTimeout(() => reject(new Error('Clipboard timed out')), 1500))
                ]);
                copied = true;
            }
        } catch (_) { /* Try the browser selection fallback below. */ }
        if (!copied) {
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.setAttribute('readonly', '');
            textarea.setAttribute('aria-label', 'Text to copy');
            textarea.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;';
            document.body.appendChild(textarea);
            textarea.focus();
            textarea.select();
            textarea.setSelectionRange(0, textarea.value.length);
            try { copied = document.execCommand('copy'); } catch (_) { copied = false; }
            textarea.remove();
            button.focus({ preventScroll: true });
        }
        button.textContent = copied ? 'Copied!' : 'Select & copy';
        feedback.textContent = copied ? 'Copied to your clipboard.' : 'Clipboard access is blocked. The text is selected: press Ctrl+C (Mac: Cmd+C), or touch and hold to copy.';
        if (!copied && source) {
            const range = document.createRange();
            range.selectNodeContents(source);
            const selection = window.getSelection();
            selection.removeAllRanges();
            selection.addRange(range);
        }
        setTimeout(() => { button.textContent = original; }, 2500);
        return copied;
    }
    window.Portal = { copyText };
    document.querySelectorAll('[data-copy-link]').forEach(button => {
        button.addEventListener('click', () => copyText(window.location.href.split('?')[0], button, document.querySelector('#share-feedback')));
    });
})();
