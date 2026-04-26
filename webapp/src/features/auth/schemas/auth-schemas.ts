import { z } from "zod";

export const loginSchema = z.object({
  email: z.email(),
  // Login must accept the seeded dev password used in CI/local stacks (Flyway seed).
  password: z.string().min(1, "Password is required"),
});

export const registerSchema = z.object({
  name: z.string().min(1).max(120),
  email: z.email(),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type RegisterFormValues = z.infer<typeof registerSchema>;
