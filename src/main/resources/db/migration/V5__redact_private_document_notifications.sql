-- Remove private document metadata previously broadcast to non-owners.
DELETE n FROM notifications n
JOIN documents d ON n.entity_type = 'DOCUMENT' AND n.entity_id = d.id
LEFT JOIN document_folders f ON f.id = d.folder_id AND f.family_id = d.family_id
WHERE (d.hidden = 1 OR f.hidden = 1) AND n.user_id <> d.uploaded_by;

UPDATE notifications n
JOIN documents d ON n.entity_type = 'DOCUMENT' AND n.entity_id = d.id
LEFT JOIN document_folders f ON f.id = d.folder_id AND f.family_id = d.family_id
SET n.title = 'Private vault activity',
    n.message = 'Open your private vault to review this document.'
WHERE d.hidden = 1 OR f.hidden = 1;
