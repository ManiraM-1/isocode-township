import { useQuery } from "@tanstack/react-query";
import { api } from "../../lib/api";
import {
  BankImportDetails,
  BankImportSummary,
  BankRow,
  Category,
  ComplaintDetail,
  ComplaintStatus,
  ComplaintSummary,
  FlatSearchResult,
  StaffMember,
} from "../../lib/types";

export type Lane = "SAFETY" | "NEEDS_REVIEW" | "OPEN" | "AWAITING_RESIDENT";

export type BoardFilters = {
  category?: Category | "";
  status?: ComplaintStatus | "";
  assigneeId?: string;
  tower?: string;
};

export const deskKeys = {
  board: ["desk", "board"] as const,
  lane: (lane: Lane, filters: BoardFilters) => ["desk", "board", lane, filters] as const,
  complaint: (id: number) => ["desk", "complaint", id] as const,
  staff: ["desk", "staff"] as const,
  imports: ["desk", "imports"] as const,
  importDetails: (id: number) => ["desk", "imports", id] as const,
  queue: ["desk", "queue"] as const,
  flatSearch: (query: string) => ["desk", "flat-search", query] as const,
};

export function useLane(lane: Lane, filters: BoardFilters) {
  return useQuery({
    queryKey: deskKeys.lane(lane, filters),
    queryFn: () => {
      const params = new URLSearchParams({ lane });
      if (filters.category) params.set("category", filters.category);
      if (filters.status) params.set("status", filters.status);
      if (filters.assigneeId) params.set("assigneeId", filters.assigneeId);
      if (filters.tower) params.set("tower", filters.tower);
      return api.get<ComplaintSummary[]>(`/api/desk/complaints?${params}`);
    },
    refetchInterval: 30_000,
  });
}

export function useDeskComplaint(id: number) {
  return useQuery({
    queryKey: deskKeys.complaint(id),
    queryFn: () => api.get<ComplaintDetail>(`/api/desk/complaints/${id}`),
    refetchInterval: (query) => (query.state.data?.classificationStatus === "PENDING" ? 3000 : false),
  });
}

export function useStaff() {
  return useQuery({
    queryKey: deskKeys.staff,
    queryFn: () => api.get<StaffMember[]>("/api/desk/staff"),
    staleTime: 10 * 60_000,
  });
}

export function useImports() {
  return useQuery({ queryKey: deskKeys.imports, queryFn: () => api.get<BankImportSummary[]>("/api/desk/bank-imports") });
}

export function useImportDetails(id: number) {
  return useQuery({
    queryKey: deskKeys.importDetails(id),
    queryFn: () => api.get<BankImportDetails>(`/api/desk/bank-imports/${id}`),
  });
}

export function useAccountsQueue() {
  return useQuery({ queryKey: deskKeys.queue, queryFn: () => api.get<BankRow[]>("/api/desk/accounts-queue") });
}

export function useFlatSearch(query: string) {
  const text = query.trim();
  return useQuery({
    queryKey: deskKeys.flatSearch(text),
    queryFn: () => api.get<FlatSearchResult[]>(`/api/desk/flats/search?q=${encodeURIComponent(text)}`),
    enabled: text.length >= 2,
    staleTime: 60_000,
  });
}
