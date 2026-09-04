/**
 * Transparent reverse proxy in front of the Railway backend.
 *
 * Why this exists: backend-production-6d37.up.railway.app only has an IPv4 address, and some
 * mobile carriers' IPv6-first networks (NAT64/464XLAT) can't reliably reach IPv4-only single-IP
 * origins — the site (served by Vercel's CDN) loads fine on those networks, but every API call
 * to the Railway backend fails. Cloudflare's edge is anycast with full IPv6 support, so fronting
 * the backend with a Worker gives it the same universal reachability the frontend already has.
 *
 * This forwards everything unchanged — method, headers, body, query string, and WebSocket
 * upgrade requests (the STOMP connection at /ws/websocket) — so the origin sees exactly what the
 * client sent, including the Origin header the backend's own CORS check relies on.
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const target = new URL(url.pathname + url.search, env.ORIGIN);
    const proxied = new Request(target, request);
    return fetch(proxied);
  },
};
