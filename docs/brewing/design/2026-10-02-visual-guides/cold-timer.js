/* Prototype clock rules. No browser or Android notification scheduling. */
'use strict';
const ColdTimer = (() => {
  const DEFAULT_SECONDS = 14 * 3600;
  const MAX_SECONDS = 7 * 24 * 3600; // Input bound, never a recipe or safety recommendation.
  function duration(hours, minutes) {
    if (hours === '' || minutes === '') return null;
    const h = Number(hours), m = Number(minutes);
    if (!Number.isInteger(h) || !Number.isInteger(m) || h < 0 || m < 0 || m > 59) return null;
    const seconds = h * 3600 + m * 60;
    return seconds >= 60 && seconds <= MAX_SECONDS ? seconds : null;
  }
  function label(seconds) {
    const h = Math.floor(seconds / 3600), m = Math.floor(seconds % 3600 / 60);
    return [h ? h + ' h' : '', m ? m + ' min' : ''].filter(Boolean).join(' ') || '0 min';
  }
  function deadline(batch) {
    if (!batch || batch.unknown || !Number.isFinite(batch.origin)) return null;
    return batch.origin + batch.durationSeconds * 1000 - (batch.offset || 0);
  }
  function ready(batch, now = Date.now()) {
    const due = deadline(batch);
    return due !== null && now >= due;
  }
  function remaining(batch, now = Date.now()) {
    const due = deadline(batch);
    return due === null ? null : Math.max(0, Math.ceil((due - now) / 1000));
  }
  function revise(batch, seconds, remind = batch.remind) {
    if (!Number.isInteger(seconds) || seconds < 60 || seconds > MAX_SECONDS) throw new RangeError('Invalid steep duration');
    if (seconds === batch.durationSeconds && !!remind === !!batch.remind) return {...batch};
    return {...batch, durationSeconds: seconds, remind: !!remind, alertRevision: (batch.alertRevision || 0) + 1};
  }
  function alertEligible(batch, now = Date.now()) {
    return !!batch && batch.index === 3 && !batch.complete && !batch.stoppedAt && batch.remind && ready(batch, now);
  }
  function previewAlert(batch, permission, now = Date.now()) {
    return alertEligible(batch, now) && permission === 'allowed' && batch.alertedRevision !== batch.alertRevision;
  }
  function foregroundAlert(batch, now = Date.now()) {
    return alertEligible(batch, now) && batch.inAppAlertedRevision !== batch.alertRevision;
  }
  function restore(saved, recipeLengths) {
    const valid = value => {
      if (!value || !recipeLengths[value.recipe] || !Number.isInteger(value.index) || value.index < 0 || value.index >= recipeLengths[value.recipe]) return null;
      for (const key of ['origin', 'offset', 'stoppedAt']) if (value[key] != null && (!Number.isFinite(value[key]) || value[key] < 0)) return null;
      for (const key of ['unknown', 'paused', 'complete', 'quick', 'remind']) if (value[key] != null && typeof value[key] !== 'boolean') return null;
      const result = {...value};
      if (result.recipe === 'cold') {
        // Preserve the reviewed 14-hour identity when migrating the first prototype.
        result.durationSeconds ??= DEFAULT_SECONDS;
        if (!Number.isInteger(result.durationSeconds) || result.durationSeconds < 60 || result.durationSeconds > MAX_SECONDS) return null;
        result.remind ??= true;
        result.alertRevision ??= 1;
        result.alertedRevision ??= 0;
        result.inAppAlertedRevision ??= 0;
        if (!Number.isInteger(result.alertRevision) || result.alertRevision < 1 || !Number.isInteger(result.alertedRevision) || result.alertedRevision < 0) return null;
        if (!Number.isInteger(result.inAppAlertedRevision) || result.inAppAlertedRevision < 0) return null;
        result.origin ??= null;
        if (result.origin === null && result.index >= 3) result.unknown = true;
        // A passive steep has no pause control: leaving the guide never freezes it.
        if (result.index === 3) result.paused = false;
      }
      return result;
    };
    if (saved?.version === 2) {
      const cold = valid(saved.cold), foreground = valid(saved.foreground);
      return {cold: cold?.recipe === 'cold' ? cold : null, foreground: foreground?.recipe !== 'cold' ? foreground : null};
    }
    const previous = valid(saved);
    return {cold: previous?.recipe === 'cold' ? previous : null, foreground: previous?.recipe !== 'cold' ? previous : null};
  }
  return {DEFAULT_SECONDS, MAX_SECONDS, duration, label, deadline, ready, remaining, revise, alertEligible, previewAlert, foregroundAlert, restore};
})();
if (typeof module !== 'undefined') module.exports = ColdTimer;
