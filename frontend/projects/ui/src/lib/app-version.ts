export const APP_VERSION = {
  version: 'dev',
  commit: 'local',
  builtAt: 'unknown',
};

export function formatAppVersion(): string {
  const { version, commit } = APP_VERSION;
  if (!commit || commit === 'local' || version.includes(commit)) {
    return version;
  }
  return `${version} · ${commit}`;
}
