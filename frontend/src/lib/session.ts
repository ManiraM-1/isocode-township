import { QueryClient, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { useToast } from "../components/Toast";
import { api, ApiError } from "./api";
import { DemoUser, Me } from "./types";

export const ME_KEY = ["me"] as const;

export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async (): Promise<Me | null> => {
      try {
        return await api.get<Me>("/api/me");
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          return null;
        }
        throw error;
      }
    },
    staleTime: 5 * 60_000,
  });
}

export function useDemoUsers() {
  return useQuery({
    queryKey: ["demo-users"],
    queryFn: async (): Promise<DemoUser[] | null> => {
      try {
        return await api.get<DemoUser[]>("/api/auth/demo-users");
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
    staleTime: Infinity,
    retry: false,
  });
}

export function useDemoMode(): boolean {
  const demoUsers = useDemoUsers();
  return !!demoUsers.data;
}

export const SIGN_OUT_FAILED =
  "We could not reach the server, so this device may still be signed in. Please try signing out again.";

export async function signOut(
  queryClient: QueryClient,
  requestLogout: () => Promise<unknown>,
  navigateToLogin: () => void,
  showError: (message: string) => void,
) {
  try {
    await requestLogout();
  } catch {
    showError(SIGN_OUT_FAILED);
  }
  await queryClient.cancelQueries();
  queryClient.setQueryData<Me | null>(ME_KEY, null);
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== ME_KEY[0] });
  navigateToLogin();
}

export function useSignOut() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();
  return () =>
    signOut(
      queryClient,
      () => api.post("/api/auth/logout"),
      () => navigate("/login", { replace: true }),
      (message) => toast(message, "error"),
    );
}

export function homePathFor(me: Me): string {
  return me.role === "HELPDESK" ? "/desk/complaints" : "/home";
}
