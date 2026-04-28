export interface AuthPreCheckRequest {
  email: string;
}

export interface AuthPreCheckResponse {
  requiresPassword: boolean;
}

export interface AuthLoginRequest {
  email: string;
  password?: string;
}
