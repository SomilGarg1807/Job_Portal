(() => {
    'use strict';
    const description = document.querySelector('#content');
    const form = document.querySelector('#job-main-form');
    const feedback = document.querySelector('#editor-feedback');
    const count = document.querySelector('#description-count');
    const hasEditor = Boolean(window.jQuery?.fn?.summernote);
    const readHtml = () => hasEditor ? window.jQuery(description).summernote('code') : description.value;
    function updateCount() {
        const length = readHtml().length;
        count.textContent = `${length.toLocaleString()} / 10,000 characters`;
        count.classList.toggle('over-limit', length > 10000);
    }
    if (hasEditor) {
        window.jQuery(description).summernote({
            height: 340,
            placeholder: 'About the role\n\nResponsibilities\n\nExperience and skills\n\nWhat we offer',
            toolbar: [['style', ['bold', 'italic', 'underline']], ['para', ['ul', 'ol', 'paragraph']], ['insert', ['link']], ['view', ['undo', 'redo']]],
            callbacks: { onChange: updateCount, onInit: () => {
                const editable = document.querySelector('.note-editable');
                editable.setAttribute('role', 'textbox');
                editable.setAttribute('aria-label', 'Job description');
                editable.setAttribute('aria-multiline', 'true');
            } }
        });
    } else {
        description.addEventListener('input', updateCount);
    }
    updateCount();
    document.querySelector('#ai-from-form').addEventListener('click', () => {
        const fields = [['title', 'Role'], ['companyName', 'Company'], ['type', 'Employment'], ['remote', 'Workplace'], ['salary', 'Salary'], ['city', 'City'], ['state', 'Region'], ['country', 'Country']];
        const context = fields.map(([id, label]) => {
            const value = document.getElementById(id).value.trim();
            return value ? `${label}: ${value}` : '';
        }).filter(Boolean).join('\n');
        document.querySelector('#ai-context').value = (context + '\nSkills and experience: ').slice(0, 3000);
        document.querySelector('#ai-context').focus();
        document.querySelector('#ai-status').textContent = 'Details added. Include the required skills and experience before generating.';
    });
    document.querySelector('#ai-use').addEventListener('click', () => {
        const text = document.querySelector('#ai-result').textContent;
        const container = document.createElement('div');
        text.split('\n').forEach(line => { const p = document.createElement('p'); p.textContent = line; container.appendChild(p); });
        const html = container.innerHTML;
        if (html.length > 10000) { document.querySelector('#ai-status').textContent = 'This draft exceeds the description limit. Copy and shorten it before adding.'; return; }
        if (hasEditor) window.jQuery(description).summernote('code', html);
        else description.value = text;
        updateCount();
        feedback.textContent = 'AI draft inserted. Review it and add any missing details before publishing.';
        feedback.scrollIntoView({ block: 'center', behavior: 'smooth' });
    });
    form.addEventListener('submit', event => {
        const html = readHtml();
        const documentText = new DOMParser().parseFromString(html, 'text/html').body.textContent.trim();
        if (documentText.length < 30 || html.length > 10000) {
            event.preventDefault();
            feedback.textContent = 'Add a description with at least 30 characters of text, within the 10,000-character limit.';
            feedback.scrollIntoView({ block: 'center' });
            return;
        }
        description.value = html;
    });
})();
