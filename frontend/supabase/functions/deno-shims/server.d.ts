// Type shim for https://deno.land/std@0.168.0/http/server.ts
// These functions are available natively in Deno Edge Functions runtime.

/**
 * Starts an HTTP server with the given handler.
 * This is a type-only shim for VS Code IntelliSense when editing Deno Edge Functions.
 */
export function serve(handler: (request: Request) => Response | Promise<Response>): void;
