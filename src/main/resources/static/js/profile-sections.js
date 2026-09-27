(() => {
    'use strict';
    document.querySelectorAll('.profile-form').forEach(form => {
        // Reveal the section before the browser focuses an invalid field.
        form.addEventListener('invalid', event => {
            const section = event.target.closest('.profile-section');
            if (section) section.open = true;
        }, true);
    });
    if (document.querySelector('.form-message.error')) {
        document.querySelectorAll('.profile-section').forEach(section => { section.open = true; });
    }
})();
