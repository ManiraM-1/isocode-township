import { useQuery, useQueryClient } from "@tanstack/react-query";
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

export function useSignOut() {
  const queryClient = useQueryClient();
  return async () => {
    try {
      await api.post("/api/auth/logout");
    } finally {
      queryClient.clear();
      queryClient.setQueryData<Me | null>(ME_KEY, null);
    }
  };
}

export function homePathFor(me: Me): string {
  return me.role === "HELPDESK" ? "/desk/complaints" : "/home";
}
