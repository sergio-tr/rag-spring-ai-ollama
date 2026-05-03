import {
  Briefcase,
  Code,
  FileText,
  FlaskConical,
  Folder,
  FolderKanban,
  MessageSquare,
  Rocket,
  Shield,
  Star,
} from "lucide-react";

type ProjectVisualProps = Readonly<{
  iconKey?: string | null;
  colorHex?: string | null;
  iconClassName?: string;
  dotClassName?: string;
}>;

function isHexColor(s: string | null | undefined): s is string {
  return Boolean(s && /^#([0-9A-Fa-f]{6})$/.test(s));
}

export function ProjectIcon({ iconKey, className }: Readonly<{ iconKey?: string | null; className?: string }>) {
  switch (iconKey) {
    case "folder":
      return <Folder className={className} aria-hidden />;
    case "briefcase":
      return <Briefcase className={className} aria-hidden />;
    case "star":
      return <Star className={className} aria-hidden />;
    case "code":
      return <Code className={className} aria-hidden />;
    case "rocket":
      return <Rocket className={className} aria-hidden />;
    case "shield":
      return <Shield className={className} aria-hidden />;
    case "chat":
      return <MessageSquare className={className} aria-hidden />;
    case "lab":
      return <FlaskConical className={className} aria-hidden />;
    case "book":
      return <FileText className={className} aria-hidden />;
    default:
      return <FolderKanban className={className} aria-hidden />;
  }
}

export function ProjectVisual({ iconKey, colorHex, iconClassName, dotClassName }: ProjectVisualProps) {
  return (
    <>
      <span
        className={dotClassName ?? "inline-block size-2.5 shrink-0 rounded-full border border-border"}
        style={{ backgroundColor: isHexColor(colorHex) ? colorHex : "#9ca3af" }}
        aria-hidden
      />
      <ProjectIcon iconKey={iconKey} className={iconClassName ?? "size-4 shrink-0"} />
    </>
  );
}
