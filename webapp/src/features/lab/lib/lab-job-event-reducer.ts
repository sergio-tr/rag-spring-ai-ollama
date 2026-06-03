import type { LabJobEventDto } from "@/types/api";
import {
  mergeLabProgressSnapshot,
  payloadNumber,
  payloadString,
  type LabProgressSnapshot,
  EMPTY_LAB_PROGRESS_SNAPSHOT,
} from "@/features/lab/lib/lab-job-progress-payload";

export type LabJobProgressPhase =
  | "ACCEPTED"
  | "DATASET"
  | "KNOWLEDGE_BASE"
  | "INDEXING"
  | "PLANNING"
  | "RUNNING"
  | "EXPORT"
  | "COMPLETED"
  | "FAILED"
  | "UNKNOWN";

export type LabSubtaskRow = {
  id: string;
  type: string;
  label: string;
  status: "done" | "running" | "failed" | "skipped";
};

export type LabJobProgressView = {
  phase: LabJobProgressPhase;
  phaseLabel: string | null;
  presetCode: string | null;
  currentModelId: string | null;
  currentItem: number | null;
  totalItems: number | null;
  globalCompleted: number | null;
  globalTotal: number | null;
  itemsCompleted: number;
  itemsFailed: number;
  itemsSkipped: number;
  lastAction: string | null;
  subtasks: LabSubtaskRow[];
  technicalEvents: LabJobEventDto[];
};

const MILESTONE_EVENT_TYPES = new Set([
  "ACCEPTED",
  "RAG_EVALUATION_ACCEPTED",
  "DATASET_RESOLVED",
  "KNOWLEDGE_BASE_CHECKED",
  "CAMPAIGN_ACCEPTED",
  "CAMPAIGN_PLANNED",
  "CAMPAIGN_STARTED",
  "RUN_STARTED",
  "PRESET_STARTED",
  "ITEM_STARTED",
  "SNAPSHOT_PREPARATION_STARTED",
  "SNAPSHOT_PREPARATION_COMPLETED",
  "EXPORT_GENERATED",
  "RUN_COMPLETED",
  "CAMPAIGN_COMPLETED",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
]);

const TECHNICAL_EVENT_TYPES = new Set(["PROGRESS", "STARTED"]);

const SPAM_MESSAGE_PATTERNS = [
  /^Resolving typed dataset/i,
  /^Auto-reindex lock acquired/i,
  /^RAG dataset resolved:/i,
  /^Parsed dataset /i,
];

function payloadPhase(event: LabJobEventDto): string | null {
  const phase = event.payload?.phase;
  return typeof phase === "string" && phase.trim() ? phase.trim() : null;
}

function eventPhase(event: LabJobEventDto): LabJobProgressPhase {
  const payload = payloadPhase(event);
  if (payload === "DATASET") return "DATASET";
  if (payload === "KNOWLEDGE_BASE") return "KNOWLEDGE_BASE";
  if (payload === "INDEXING") return "INDEXING";
  if (payload === "RAG_EVALUATION") return "RUNNING";

  switch (event.type) {
    case "ACCEPTED":
    case "RAG_EVALUATION_ACCEPTED":
      return "ACCEPTED";
    case "DATASET_RESOLVED":
      return "DATASET";
    case "KNOWLEDGE_BASE_CHECKED":
      return "KNOWLEDGE_BASE";
    case "SNAPSHOT_PREPARATION_STARTED":
    case "SNAPSHOT_PREPARATION_COMPLETED":
      return "INDEXING";
    case "CAMPAIGN_ACCEPTED":
    case "CAMPAIGN_PLANNED":
      return "PLANNING";
    case "CAMPAIGN_STARTED":
    case "RUN_STARTED":
    case "PRESET_STARTED":
    case "ITEM_STARTED":
    case "ITEM_COMPLETED":
    case "ITEM_FAILED":
      return "RUNNING";
    case "EXPORT_GENERATED":
      return "EXPORT";
    case "RUN_COMPLETED":
    case "CAMPAIGN_COMPLETED":
    case "COMPLETED":
      return "COMPLETED";
    case "FAILED":
    case "CANCELLED":
      return "FAILED";
    default:
      return "UNKNOWN";
  }
}

function isSpamUserMessage(message: string | null | undefined): boolean {
  if (!message?.trim()) return false;
  return SPAM_MESSAGE_PATTERNS.some((re) => re.test(message.trim()));
}

function subtaskStatusForType(type: string): LabSubtaskRow["status"] {
  if (type === "ITEM_FAILED" || type === "FAILED") return "failed";
  if (type === "ITEM_SKIPPED") return "skipped";
  if (type === "ITEM_STARTED") return "running";
  return "done";
}

function milestoneLabel(event: LabJobEventDto): string {
  const payloadLabel = event.payload?.label;
  if (typeof payloadLabel === "string" && payloadLabel.trim()) {
    return payloadLabel.trim();
  }
  if (event.message?.trim() && !isSpamUserMessage(event.message)) {
    return event.message.trim();
  }
  return event.type.replaceAll("_", " ").toLowerCase();
}

function upsertSubtask(rows: LabSubtaskRow[], event: LabJobEventDto): void {
  if (!MILESTONE_EVENT_TYPES.has(event.type) || event.type === "ITEM_COMPLETED") {
    return;
  }
  const key = `${event.type}:${milestoneLabel(event)}`;
  const row: LabSubtaskRow = {
    id: key,
    type: event.type,
    label: milestoneLabel(event),
    status: subtaskStatusForType(event.type),
  };
  const idx = rows.findIndex((r) => r.id === key);
  if (idx >= 0) {
    rows[idx] = row;
  } else {
    rows.push(row);
  }
}

/** Reduces SSE events into operator-facing progress (no repeated technical spam). */
export function reduceLabJobEvents(
  events: LabJobEventDto[],
  seed: LabProgressSnapshot = EMPTY_LAB_PROGRESS_SNAPSHOT,
): LabJobProgressView {
  const subtasks: LabSubtaskRow[] = [];
  const technicalEvents: LabJobEventDto[] = [];
  let phase: LabJobProgressPhase = "ACCEPTED";
  let phaseLabel: string | null = null;
  let presetCode: string | null = seed.currentPresetCode;
  let currentModelId: string | null = seed.currentModelId;
  let currentItem: number | null = seed.currentItem;
  let totalItems: number | null = seed.totalItems;
  let globalCompleted: number | null = seed.globalCompleted;
  let globalTotal: number | null = seed.globalTotal;
  let itemsCompleted = seed.globalCompleted ?? 0;
  let itemsFailed = 0;
  let itemsSkipped = 0;
  let lastAction: string | null = seed.lastUserMessage;

  for (const event of events) {
    if (event.type === "HEARTBEAT" || event.type === "SNAPSHOT") continue;

    if (TECHNICAL_EVENT_TYPES.has(event.type) || isSpamUserMessage(event.message)) {
      technicalEvents.push(event);
      continue;
    }

    const payload = event.payload ?? undefined;
    const payloadUser = payloadString(payload, "userMessage");

    if (event.type === "ITEM_COMPLETED" || event.type === "ITEM_STARTED") {
      if (event.globalCompletedItems != null) {
        globalCompleted = event.globalCompletedItems;
      }
      const payloadCurrent = payloadNumber(payload, "currentItem");
      if (payloadCurrent != null) {
        currentItem = payloadCurrent;
        if (event.type === "ITEM_COMPLETED") {
          globalCompleted = payloadCurrent;
        }
      } else if (event.globalCompletedItems != null) {
        currentItem = event.globalCompletedItems;
      } else if (event.runCompletedItems != null) {
        currentItem = event.runCompletedItems;
      }
      const payloadTotal = payloadNumber(payload, "totalItems");
      if (payloadTotal != null) {
        globalTotal = payloadTotal;
        totalItems = payloadTotal;
      } else if (event.globalTotalItems != null) {
        globalTotal = event.globalTotalItems;
        totalItems = event.globalTotalItems;
      } else if (event.runTotalItems != null) {
        totalItems = event.runTotalItems;
      }
      if (event.type === "ITEM_COMPLETED") {
        itemsCompleted =
          globalCompleted != null ? globalCompleted : itemsCompleted + 1;
      }
      lastAction = payloadUser ?? event.message?.trim() ?? lastAction;
    } else if (event.type === "ITEM_FAILED") {
      itemsFailed += 1;
    } else if (event.type === "ITEM_SKIPPED") {
      itemsSkipped += 1;
    }

    phase = eventPhase(event);
    if (event.message?.trim() && !isSpamUserMessage(event.message)) {
      phaseLabel = event.message.trim();
      if (event.type === "ITEM_COMPLETED" || event.type === "ITEM_STARTED") {
        lastAction = phaseLabel;
      }
    }

    if (event.currentPresetCode?.trim()) {
      presetCode = event.currentPresetCode.trim();
    }
    if (event.currentModelId?.trim()) {
      currentModelId = event.currentModelId.trim();
    }

    if (event.globalCompletedItems != null) {
      globalCompleted = event.globalCompletedItems;
      currentItem = event.globalCompletedItems;
    } else if (event.runCompletedItems != null) {
      currentItem = event.runCompletedItems;
    }
    if (event.globalTotalItems != null) {
      globalTotal = event.globalTotalItems;
      totalItems = event.globalTotalItems;
    } else if (event.runTotalItems != null) {
      totalItems = event.runTotalItems;
    }

    upsertSubtask(subtasks, event);
  }

  return {
    phase,
    phaseLabel,
    presetCode,
    currentModelId,
    currentItem,
    totalItems,
    globalCompleted,
    globalTotal,
    itemsCompleted,
    itemsFailed,
    itemsSkipped,
    lastAction,
    subtasks,
    technicalEvents: technicalEvents.slice(-20),
  };
}


export function progressPercent(view: LabJobProgressView): number | null {
  const done = view.globalCompleted ?? view.currentItem;
  const total = view.globalTotal ?? view.totalItems;
  if (done == null || total == null || total <= 0) {
    return null;
  }
  return Math.min(100, Math.round((done / total) * 100));
}
