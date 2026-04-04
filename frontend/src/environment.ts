export const environment = {
  /**
   * Empty string = same origin as the Angular dev server (`ng serve`).
   * `/api/*` is forwarded to Spring Boot via `proxy.conf.json` → no browser CORS issues.
   * For a deployed build on a different host than the API, set the full backend URL here.
   */
  apiBaseUrl: '',
  enableBackendAuthSync: true,
};
