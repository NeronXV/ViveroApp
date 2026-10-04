// Preserve microseconds as text, never round-trip through a JS Date.
export function promotionDate(value) {
  if (value === null) return null;
  if (typeof value !== 'string' || !/^[1-9][0-9]{3}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.(\d{3}|\d{6})Z$/.test(value)) throw new Error('Invalid UTC date');
  const milliseconds = value.slice(0, 23) + 'Z';
  const parsed = new Date(milliseconds);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString() !== milliseconds) throw new Error('Invalid UTC date');
  return value.slice(0, 19).replace('T', ' ') + '.' + value.slice(20, -1).padEnd(6, '0');
}
