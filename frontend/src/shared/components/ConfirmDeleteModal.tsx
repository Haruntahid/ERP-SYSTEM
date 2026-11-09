import { useState, useEffect, FormEvent } from 'react';

export interface DeleteTarget {
  title: string;
  description?: string;
  onConfirm: () => void | Promise<void>;
}

interface ConfirmDeleteModalProps {
  target: DeleteTarget | null;
  onClose: () => void;
}

export function ConfirmDeleteModal({ target, onClose }: ConfirmDeleteModalProps) {
  const [typedText, setTypedText] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (target) {
      setTypedText('');
      setSubmitting(false);
    }
  }, [target]);

  if (!target) return null;

  const isMatched = typedText.trim().toLowerCase() === 'delete';

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isMatched || submitting) return;
    try {
      setSubmitting(true);
      await target.onConfirm();
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card stack" onClick={(e) => e.stopPropagation()}>
        <div className="page-header">
          <h3 style={{ color: '#dc2626' }}>Confirm Permanent Action: {target.title}</h3>
          <button type="button" className="secondary" onClick={onClose}>
            &times;
          </button>
        </div>
        <p className="muted">
          {target.description ??
            'To prevent accidental deletion, please type "delete" in the box below to confirm.'}
        </p>
        <form className="stack" onSubmit={(e) => void handleSubmit(e)}>
          <label>
            Type <code>delete</code> to confirm
            <input
              autoFocus
              value={typedText}
              onChange={(e) => setTypedText(e.target.value)}
              placeholder="delete"
            />
          </label>
          <div className="action-row" style={{ justifyContent: 'flex-end' }}>
            <button type="button" className="secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="danger" disabled={!isMatched || submitting}>
              {submitting ? 'Deleting...' : 'Confirm Delete'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

