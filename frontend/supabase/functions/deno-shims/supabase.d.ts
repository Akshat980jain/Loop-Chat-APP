// Type shim for https://esm.sh/@supabase/supabase-js@2.38.4
// Self-contained declaration for VS Code IntelliSense in Deno Edge Functions.

export interface SupabaseClientOptions {
  auth?: {
    autoRefreshToken?: boolean;
    persistSession?: boolean;
    detectSessionInUrl?: boolean;
  };
  global?: {
    headers?: Record<string, string>;
  };
}

export interface PostgrestResponse<T> {
  data: T | null;
  error: { message: string; details?: string; hint?: string; code?: string } | null;
  count?: number | null;
  status: number;
  statusText: string;
}

export interface PostgrestBuilder<T> extends Promise<PostgrestResponse<T>> {
  select(columns?: string): this;
  insert(values: object | object[]): this;
  update(values: object): this;
  delete(): this;
  eq(column: string, value: unknown): this;
  neq(column: string, value: unknown): this;
  gt(column: string, value: unknown): this;
  lt(column: string, value: unknown): this;
  gte(column: string, value: unknown): this;
  lte(column: string, value: unknown): this;
  like(column: string, pattern: string): this;
  ilike(column: string, pattern: string): this;
  is(column: string, value: unknown): this;
  in(column: string, values: unknown[]): this;
  not(column: string, filter: string, value: unknown): this;
  or(filters: string): this;
  filter(column: string, operator: string, value: unknown): this;
  order(column: string, options?: { ascending?: boolean }): this;
  limit(count: number): this;
  range(from: number, to: number): this;
  single(): Promise<PostgrestResponse<T>>;
  maybeSingle(): Promise<PostgrestResponse<T | null>>;
}

export interface SupabaseClient {
  from(table: string): PostgrestBuilder<any>;
  rpc(fn: string, params?: object): PostgrestBuilder<any>;
  auth: {
    getUser(jwt?: string): Promise<{ data: { user: any }; error: any }>;
    signOut(): Promise<{ error: any }>;
  };
  storage: {
    from(bucket: string): {
      upload(path: string, file: Blob | File | ArrayBuffer, options?: object): Promise<{ data: any; error: any }>;
      download(path: string): Promise<{ data: Blob | null; error: any }>;
      getPublicUrl(path: string): { data: { publicUrl: string } };
      remove(paths: string[]): Promise<{ data: any; error: any }>;
    };
  };
}

export function createClient(
  supabaseUrl: string,
  supabaseKey: string,
  options?: SupabaseClientOptions
): SupabaseClient;
