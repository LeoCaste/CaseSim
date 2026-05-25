import { Injectable } from '@angular/core';

const AUTH_USER_STORAGE_KEY = 'casesim.auth.user';
const AUTH_TOKEN_STORAGE_KEY = 'casesim.auth.token';

@Injectable({
  providedIn: 'root'
})
export class JwtStorageService {
  getStoredToken(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
  }

  setStoredToken(token: string): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);
  }

  clearStoredToken(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  }

  setStoredUser(rawUser: string): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.setItem(AUTH_USER_STORAGE_KEY, rawUser);
  }

  getStoredUser(): string | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }

    return localStorage.getItem(AUTH_USER_STORAGE_KEY);
  }

  clearStoredUser(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }

    localStorage.removeItem(AUTH_USER_STORAGE_KEY);
  }
}
