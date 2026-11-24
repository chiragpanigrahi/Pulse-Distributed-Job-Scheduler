import React from 'react'
import { Modal } from './ui'

export default function ConfirmDialog({ title, message, confirmLabel = 'Confirm', onConfirm, onCancel }) {
  return (
    <Modal
      title={title}
      onClose={onCancel}
      footer={(
        <>
          <button onClick={onCancel} className="btn-ghost">Cancel</button>
          <button onClick={onConfirm} className="btn-danger">{confirmLabel}</button>
        </>
      )}
    >
      <p className="text-sm text-ink-soft">{message}</p>
    </Modal>
  )
}
