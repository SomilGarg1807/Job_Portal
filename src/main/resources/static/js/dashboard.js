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
})();
