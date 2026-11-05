import { QueryClient, QueryObserver } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { ME_KEY, SIGN_OUT_FAILED, signOut } from "./session";
import { Me } from "./types";

const SWATI: Me = { id: 2, name: "Swati Joshi", role: "RESIDENT", isDutyManager: false, flats: [] };

function signedInClient() {
  const client = new QueryClient();
  client.setQueryData(ME_KEY, SWATI);
  client.setQueryData(["flat", 2, "summary"], { outstanding: "100.00" });
  const seen: unknown[] = [];
  const observer = new QueryObserver<Me | null>(client, { queryKey: ME_KEY, staleTime: Infinity });
  const unsubscribe = observer.subscribe((result) => seen.push(result.data));
  return { client, seen, unsubscribe };
}

describe("sign out", () => {
  it("tells the mounted session query, drops cached data and goes to login", async () => {
    const { client, seen, unsubscribe } = signedInClient();
    const toLogin = vi.fn();
    const showError = vi.fn();

    await signOut(client, () => Promise.resolve(), toLogin, showError);

    expect(seen.at(-1)).toBeNull();
    expect(client.getQueryData(["flat", 2, "summary"])).toBeUndefined();
    expect(toLogin).toHaveBeenCalledOnce();
    expect(showError).not.toHaveBeenCalled();
    unsubscribe();
  });

  it("still clears local state and shows an error when the request fails", async () => {
    const { client, seen, unsubscribe } = signedInClient();
    const toLogin = vi.fn();
    const showError = vi.fn();

    await signOut(client, () => Promise.reject(new Error("offline")), toLogin, showError);

    expect(seen.at(-1)).toBeNull();
    expect(client.getQueryData(["flat", 2, "summary"])).toBeUndefined();
    expect(toLogin).toHaveBeenCalledOnce();
    expect(showError).toHaveBeenCalledWith(SIGN_OUT_FAILED);
    unsubscribe();
  });
});
