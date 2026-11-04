import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api } from "../../lib/api";
import { useDemoMode } from "../../lib/session";
import { Button } from "../../components/Button";
import { useToast } from "../../components/Toast";

export function DemoResetButton({ compact = false }: { compact?: boolean }) {
  const demoMode = useDemoMode();
  const [confirming, setConfirming] = useState(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();

  const reset = useMutation({
    mutationFn: () => api.post<{ resetAt: string }>("/api/desk/demo/reset"),
    onSuccess: async () => {
      setConfirming(false);
      await queryClient.resetQueries();
      navigate("/desk/complaints", { replace: true });
      toast("Demo data reset to the starting scenario");
    },
    onError: (error) => toast(error instanceof Error ? error.message : "Reset failed", "error"),
  });

  if (!demoMode) return null;

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className={
          compact
            ? "min-h-touch px-2 text-sm text-ink-soft hover:text-ink"
            : "min-h-touch w-full rounded-xl px-3 text-left text-sm text-ink-soft hover:bg-canvas hover:text-ink"
        }
      >
        Reset demo data
      </button>
    );
  }

  return (
    <div
      role="alertdialog"
      aria-label="Reset demo data"
      className={`rounded-xl border border-danger/40 bg-danger-soft/60 p-3 text-sm ${
        compact ? "absolute right-3 top-12 z-40 w-64 shadow-lg" : ""
      }`}
    >
      <p className="text-ink">Erase every change and restore the demo scenario as of today?</p>
      <div className="mt-2 flex gap-2">
        <Button size="sm" variant="danger" busy={reset.isPending} onClick={() => reset.mutate()}>
          Reset now
        </Button>
        <Button size="sm" variant="ghost" disabled={reset.isPending} onClick={() => setConfirming(false)}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
