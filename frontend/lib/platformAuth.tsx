"use client";

import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { useRouter } from "next/navigation";
import { api, PlatformAuthResponse } from "@/lib/api";

interface PlatformAuthContextValue {
  session: PlatformAuthResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  logout: () => void;
}

const PlatformAuthContext = createContext<PlatformAuthContextValue | undefined>(undefined);

const STORAGE_KEY = "rbms_platform_session";

export function PlatformAuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<PlatformAuthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
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

  function persist(next: PlatformAuthResponse | null) {
    setSession(next);
    if (next) {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } else {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }

  async function login(email: string, password: string) {
    const res = await api.platformLogin(email, password);
    persist(res);
    router.push("/platform");
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    if (!session) return;
    await api.platformChangePassword(session.token, currentPassword, newPassword);
    persist({ ...session, mustChangePassword: false });
  }

  function logout() {
    persist(null);
    api.platformLogout().catch(() => {});
    router.push("/platform/login");
  }

  return (
    <PlatformAuthContext.Provider value={{ session, loading, login, changePassword, logout }}>
      {children}
    </PlatformAuthContext.Provider>
  );
}

export function usePlatformAuth() {
  const ctx = useContext(PlatformAuthContext);
  if (!ctx) throw new Error("usePlatformAuth must be used within PlatformAuthProvider");
  return ctx;
}
