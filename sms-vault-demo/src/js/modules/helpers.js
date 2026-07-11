/**
 * Utility functions for date, string and format helpers.
 * This file has no dependencies on other modules.
 */

export function escapeHtml(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function timestamp() {
  return new Date().toLocaleTimeString('fr-FR', { hour12: false });
}

export function formatDate(ts) {
  return new Date(ts).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
}

export function formatShortTime(ts) {
  return new Date(ts).toLocaleTimeString('fr-FR', { hour12: false });
}

export function mapTypeToString(type) {
  switch (type) {
    case 1: return 'inbox';
    case 2: return 'sent';
    case 3: return 'draft';
    case 4: return 'outbox';
    case 5: return 'failed';
    case 6: return 'queued';
    default: return 'all';
  }
}
