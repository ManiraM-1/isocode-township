import { titleCase } from "./format";
import { Category, ComplaintStatus, TimelineEntry, Urgency } from "./types";

export const CATEGORY_LABEL: Record<Category, string> = {
  PLUMBING: "Plumbing",
  ELECTRICAL: "Electrical",
  LIFT: "Lift",
  SECURITY: "Security",
  HOUSEKEEPING: "Housekeeping",
  CIVIL: "Civil",
  PEST_CONTROL: "Pest control",
  PARKING: "Parking",
  NOISE: "Noise",
  OTHER: "Other",
};

export const URGENCY_LABEL: Record<Urgency, string> = {
  CRITICAL: "Critical",
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
};

const STATUS_WORDS: Record<ComplaintStatus, string> = {
  OPEN: "open",
  ASSIGNED: "assigned",
  IN_PROGRESS: "in progress",
  RESOLVED: "resolved",
  CLOSED: "closed",
};

function statusWord(value: string | null): string {
  return value && value in STATUS_WORDS ? STATUS_WORDS[value as ComplaintStatus] : titleCase(value ?? "");
}

export function classificationLabel(value: string | null): string {
  if (!value) return "";
  const [category, urgency] = value.split("/");
  const categoryLabel = CATEGORY_LABEL[category as Category] ?? titleCase(category);
  const urgencyLabel = urgency ? URGENCY_LABEL[urgency as Urgency] ?? titleCase(urgency) : "";
  return urgencyLabel ? `${categoryLabel}, ${urgencyLabel.toLowerCase()}` : categoryLabel;
}

export function describeEvent(entry: TimelineEntry): string {
  switch (entry.type) {
    case "CREATED":
      return "Complaint raised";
    case "AUTO_ASSIGNED":
      return entry.to ? `Assigned to ${entry.to}` : "Assigned";
    case "ASSIGNEE_CHANGED":
      return entry.to ? `Handed to ${entry.to}` : "Reassigned";
    case "STATUS_CHANGED":
      return `Marked ${statusWord(entry.to)}`;
    case "COMMENTED":
      return "Update from the help desk";
    case "CLOSED":
      return "Closed by the resident";
    case "REOPENED":
      return "Reopened by the resident";
    case "CATEGORY_CHANGED":
      return entry.from === entry.to
        ? `Category confirmed: ${classificationLabel(entry.to)}`
        : `Category changed to ${classificationLabel(entry.to)}`;
    case "CLASSIFIED":
      return `Sorted as ${classificationLabel(entry.to)}`;
    case "AI_SUGGESTED":
      return `AI suggested ${classificationLabel(entry.to)}`;
    case "ESCALATED":
      return "Escalated to the duty manager";
    case "ESCALATION_ACKNOWLEDGED":
      return "Duty manager alerted and acknowledged";
    case "LINKED":
      return entry.to ? `Linked with ${entry.to}` : "Linked";
    case "UNLINKED":
      return entry.to ? `Unlinked from ${entry.to}` : "Unlinked";
    default:
      return titleCase(entry.type);
  }
}

export function fitWithin(width: number, height: number, maxSide: number): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= maxSide) {
    return { width, height };
  }
  const scale = maxSide / longest;
  return { width: Math.round(width * scale), height: Math.round(height * scale) };
}

export const MAX_PHOTO_SIDE = 1600;

export async function resizePhoto(file: File, maxSide = MAX_PHOTO_SIDE): Promise<Blob> {
  if (!file.type.startsWith("image/")) {
    throw new Error("Choose a photo (JPEG, PNG or WebP).");
  }
  const bitmap = await createImageBitmap(file);
  const size = fitWithin(bitmap.width, bitmap.height, maxSide);
  const canvas = document.createElement("canvas");
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("This browser could not prepare the photo.");
  }
  context.drawImage(bitmap, 0, 0, size.width, size.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("This browser could not prepare the photo."))),
      "image/jpeg",
      0.85,
    ),
  );
}
