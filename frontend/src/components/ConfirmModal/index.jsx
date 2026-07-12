import { BsExclamationTriangleFill, BsCloudUploadFill } from 'react-icons/bs';
import './index.css';

/**
 * Generic confirmation dialog.
 *
 * Used for both "End Interview" and "Submit Solution" actions so the wording
 * and button labels can be tailored per use.
 *
 * Props:
 *  - open:         whether the dialog is visible
 *  - ending:       disables both buttons while the request is in flight
 *  - onCancel:     dismiss (e.g. "Continue Interview" / "Cancel")
 *  - onConfirm:    proceed (e.g. "End Interview" / "Submit")
 *  - title/message/confirmLabel/cancelLabel/icon: customizable copy
 */
const ConfirmModal = ({
  open,
  ending = false,
  onCancel,
  onConfirm,
  title = 'End Interview?',
  message = "Are you sure you want to end the interview? Your progress so far will be saved and you'll be taken to the interview summary.",
  confirmLabel = 'End Interview',
  cancelLabel = 'Continue Interview',
  icon = <BsExclamationTriangleFill />,
}) => {
  if (!open) return null;

  return (
    <div
      className="confirm-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-title"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !ending) onCancel();
      }}
    >
      <div className="confirm-modal">
        <div className="confirm-icon" aria-hidden="true">
          {icon}
        </div>
        <h2 id="confirm-title" className="confirm-title">
          {title}
        </h2>
        <p className="confirm-text">{message}</p>
        <div className="confirm-actions">
          <button
            type="button"
            className="confirm-btn confirm-cancel"
            onClick={onCancel}
            disabled={ending}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className="confirm-btn confirm-confirm"
            onClick={onConfirm}
            disabled={ending}
          >
            {ending ? 'Please wait…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConfirmModal;
