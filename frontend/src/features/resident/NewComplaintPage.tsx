import { ChangeEvent, FormEvent, useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { api, ApiError } from "../../lib/api";
import { resizePhoto } from "../../lib/complaints";
import { ComplaintDetail } from "../../lib/types";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { Field } from "../../components/Field";
import { Page } from "../../components/Page";
import { InlineError } from "../../components/States";
import { residentKeys } from "./queries";
import { useResidentFlat } from "./ResidentLayout";

const MIN_LENGTH = 10;
const MAX_LENGTH = 1000;

export function descriptionError(text: string): string | null {
  const length = text.trim().length;
  if (length === 0) return "Describe the problem";
  if (length < MIN_LENGTH) return `Add a little more detail (at least ${MIN_LENGTH} characters)`;
  if (length > MAX_LENGTH) return `Keep it under ${MAX_LENGTH} characters`;
  return null;
}

export function NewComplaintPage() {
  const { flatId, flatCode } = useResidentFlat();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [description, setDescription] = useState("");
  const [touched, setTouched] = useState(false);
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [preparing, setPreparing] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const validation = descriptionError(description);

  useEffect(() => () => {
    if (photoUrl) URL.revokeObjectURL(photoUrl);
  }, [photoUrl]);

  const raise = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append("description", description.trim());
      if (photo) form.append("photo", photo, "photo.jpg");
      return api.upload<ComplaintDetail>(`/api/flats/${flatId}/complaints`, form);
    },
    onSuccess: (complaint) => {
      queryClient.invalidateQueries({ queryKey: residentKeys.complaints(flatId) });
      queryClient.setQueryData(residentKeys.complaint(complaint.id), complaint);
      navigate(`/complaints/${complaint.id}?new=1`, { replace: true });
    },
  });

  async function choosePhoto(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setPhotoError(null);
    setPreparing(true);
    try {
      const resized = await resizePhoto(file);
      setPhoto(resized);
      setPhotoUrl(URL.createObjectURL(resized));
    } catch (error) {
      setPhotoError(error instanceof Error ? error.message : "That photo could not be used.");
    } finally {
      setPreparing(false);
    }
  }

  function removePhoto() {
    setPhoto(null);
    setPhotoUrl(null);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (!validation && !preparing) raise.mutate();
  }

  const serverError = raise.error instanceof ApiError ? raise.error.fieldMessage("description") : undefined;
  const serverPhotoError = raise.error instanceof ApiError ? raise.error.fieldMessage("photo") : undefined;

  return (
    <Page title="New complaint" subtitle={`Flat ${flatCode}`} back={{ to: "/complaints", label: "Complaints" }}>
      <form onSubmit={submit} noValidate>
        <Card className="space-y-5 p-5">
          <Field
            label="What is the problem?"
            error={(touched && validation) || serverError}
            hint="Where it is and since when helps us send the right person. Any language is fine."
          >
            {({ id, describedBy, invalid }) => (
              <>
                <textarea
                  id={id}
                  aria-describedby={describedBy}
                  aria-invalid={invalid}
                  rows={5}
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  onBlur={() => setTouched(true)}
                  placeholder="Water is leaking from the bathroom ceiling since this morning."
                  className={`block w-full rounded-xl border bg-surface px-3.5 py-3 text-[15px] text-ink placeholder:text-ink-faint focus:outline-none focus:ring-2 focus:ring-accent/30 ${
                    invalid ? "border-danger" : "border-line-strong focus:border-accent"
                  }`}
                />
                <p className="mt-1 text-right text-xs tabular-nums text-ink-faint">
                  {description.trim().length} / {MAX_LENGTH}
                </p>
              </>
            )}
          </Field>

          <div>
            <p className="mb-1.5 text-sm font-medium text-ink">Photo (optional)</p>
            {photoUrl ? (
              <div className="flex items-center gap-4">
                <img src={photoUrl} alt="Selected photo" className="h-24 w-24 rounded-xl border border-line object-cover" />
                <Button variant="ghost" size="sm" onClick={removePhoto}>
                  Remove photo
                </Button>
              </div>
            ) : (
              <Button variant="secondary" busy={preparing} onClick={() => fileInput.current?.click()}>
                Add a photo
              </Button>
            )}
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              tabIndex={-1}
              aria-hidden
              onChange={choosePhoto}
            />
            {(photoError || serverPhotoError) && (
              <p className="mt-1.5 text-sm text-danger">{photoError ?? serverPhotoError}</p>
            )}
          </div>

          {!serverError && !serverPhotoError && <InlineError error={raise.error} />}
          <Button type="submit" block busy={raise.isPending} disabled={preparing}>
            Send to help desk
          </Button>
          <p className="text-xs text-ink-faint">
            Mentions of fire, gas, smoke, shocks or someone stuck in a lift go straight to the duty manager.
          </p>
        </Card>
      </form>
    </Page>
  );
}
