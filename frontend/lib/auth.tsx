"use client";

import { createContext, useContext, useEffect, useState, ReactNode, useCallback } from "react";
import { useRouter } from "next/navigation";
import { api, AuthResponse, BusinessSummary, GoogleRegisterPayload, LoginPayload, RegisterBusinessPayload } from "@/lib/api";

interface AuthContextValue {
  session: AuthResponse | null;
  business: BusinessSummary | null;
  loading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterBusinessPayload) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<void>;
  registerWithGoogle: (payload: GoogleRegisterPayload) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  refreshBusiness: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const STORAGE_KEY = "rbms_session";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthResponse | null>(null);
  const [business, setBusiness] = useState<BusinessSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  const refreshBusiness = useCallback(async () => {
    if (!session) return;
    const biz = await api.getMyBusiness(session.token);
    setBusiness(biz);
  }, [session]);

  useEffect(() => {
    // NOTE: localStorage is fine for a V1 dev build. For production, prefer an
    // httpOnly cookie set by a server route so the token isn't reachable by JS/XSS.
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw) {
      try {
        setSession(JSON.parse(raw));
      } catch {
        window.localStorage.removeItem(STORAGE_KEY);
      }
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    if (!session) {
      setBusiness(null);
      return;
    }
    api.getMyBusiness(session.token).then(setBusiness).catch(() => {});
  }, [session]);

  function persist(next: AuthResponse | null) {
    setSession(next);
    if (next) {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } else {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }

  async function login(payload: LoginPayload) {
    const res = await api.login(payload);
    persist(res);
    router.push("/dashboard");
  }

  async function register(payload: RegisterBusinessPayload) {
    const res = await api.registerBusiness(payload);
    persist(res);
    router.push("/dashboard");
  }

  async function loginWithGoogle(idToken: string) {
    const res = await api.loginWithGoogle(idToken);
    persist(res);
    router.push("/dashboard");
  }

  async function registerWithGoogle(payload: GoogleRegisterPayload) {
    const res = await api.registerWithGoogle(payload);
    persist(res);
    router.push("/dashboard");
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    if (!session) return;
    await api.changePassword(session.token, currentPassword, newPassword);
    persist({ ...session, mustChangePassword: false });
  }

  function logout() {
    persist(null);
    api.logout().catch(() => {}); // best-effort — local state is already cleared either way
    router.push("/login");
  }

  return (
    <AuthContext.Provider
      value={{ session, business, loading, login, register, loginWithGoogle, registerWithGoogle, changePassword, refreshBusiness, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
