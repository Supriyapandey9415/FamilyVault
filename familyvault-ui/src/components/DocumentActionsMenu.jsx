import React from 'react';
import {
    Download,
    ExternalLink,
    Eye,
    EyeOff,
    MoreVertical,
    Pencil,
    Trash2,
} from 'lucide-react';

const DocumentActionsMenu = ({
    document,
    isOpen,
    alignUp = false,
    isOpening = false,
    isDownloading = false,
    isVisibilityUpdating = false,
    canChangeVisibility = false,
    canDelete = false,
    onToggle,
    onClose,
    onPreview,
    onDownload,
    onEdit,
    onToggleVisibility,
    onDelete,
}) => {
    const menuId = `document-actions-${document.id}`;
    const runAction = (action) => {
        onClose();
        action();
    };

    return (
        <div className="doc-actions-wrap" onClick={(event) => event.stopPropagation()}>
            <button
                type="button"
                className="doc-kebab-trigger"
                aria-label={`Actions for ${document.documentName}`}
                aria-haspopup="menu"
                aria-controls={isOpen ? menuId : undefined}
                aria-expanded={isOpen}
                onClick={(event) => {
                    event.stopPropagation();
                    onToggle();
                }}
            >
                <MoreVertical size={18} />
            </button>

            {isOpen && (
                <div
                    id={menuId}
                    className={`doc-action-menu ${alignUp ? 'align-up' : ''}`}
                    role="menu"
                    onClick={(event) => event.stopPropagation()}
                >
                    <button type="button" role="menuitem" disabled={isOpening} onClick={() => runAction(onPreview)}>
                        {isOpening ? <div className="action-spinner" /> : <ExternalLink size={15} />}
                        Preview
                    </button>
                    <button type="button" role="menuitem" disabled={isDownloading} onClick={() => runAction(onDownload)}>
                        {isDownloading ? <div className="action-spinner" /> : <Download size={15} />}
                        Download
                    </button>

                    {canChangeVisibility && (
                        <>
                            <span className="doc-action-menu-divider" />
                            <button type="button" role="menuitem" onClick={() => runAction(onEdit)}>
                                <Pencil size={15} /> Edit details
                            </button>
                            <button
                                type="button"
                                role="menuitem"
                                disabled={isVisibilityUpdating}
                                onClick={() => runAction(onToggleVisibility)}
                            >
                                {document.hidden ? <Eye size={15} /> : <EyeOff size={15} />}
                                {document.hidden ? 'Move to family documents' : 'Move to private vault'}
                            </button>
                        </>
                    )}

                    {canDelete && (
                        <>
                            <span className="doc-action-menu-divider" />
                            <button type="button" role="menuitem" className="danger" onClick={() => runAction(onDelete)}>
                                <Trash2 size={15} /> Delete document
                            </button>
                        </>
                    )}
                </div>
            )}
        </div>
    );
};

export default DocumentActionsMenu;
