import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import { z } from "zod";
import { MessageSquare, ShieldCheck, WifiOff, Eye, EyeOff, UserCircle, ArrowLeft, Mail, CheckCircle, KeyRound, Phone, Smartphone, ShieldAlert } from "lucide-react";
import { useTrackSession } from "@/hooks/useSessionManagement";

const authSchema = z.object({
  email: z.string().email("Invalid email address").max(255, "Email too long"),
  password: z.string().min(6, "Password must be at least 6 characters").max(100, "Password too long"),
});

const phoneLoginSchema = z.object({
  phone: z.string().regex(/^\+?[1-9]\d{1,14}$/, "Invalid phone number format (use international format)"),
  password: z.string().min(6, "Password must be at least 6 characters").max(100, "Password too long"),
});

const signupSchema = authSchema.extend({
  fullName: z.string().min(2, "Name must be at least 2 characters").max(100, "Name too long"),
  phone: z.string().regex(/^\+?[1-9]\d{1,14}$/, "Invalid phone number format (use international format)"),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

const forgotPasswordEmailSchema = z.object({
  email: z.string().email("Invalid email address").max(255, "Email too long"),
});

const forgotPasswordPhoneSchema = z.object({
  phone: z.string().regex(/^\+?[1-9]\d{1,14}$/, "Invalid phone number format"),
});

const otpSchema = z.object({
  otp: z.string().length(6, "OTP must be 6 digits").regex(/^\d+$/, "OTP must contain only numbers"),
  password: z.string().min(6, "Password must be at least 6 characters").max(100, "Password too long"),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

const updatePasswordSchema = z.object({
  password: z.string().min(6, "Password must be at least 6 characters").max(100, "Password too long"),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

type AuthView = "login" | "signup" | "forgot-password" | "update-password" | "verify-otp";
type LoginMethod = "email" | "phone";
type ResetMethod = "email" | "phone";

const OTP_COOLDOWN_SECONDS = 60;

const Auth = () => {
  const [view, setView] = useState<AuthView>("login");
  const [loginMethod, setLoginMethod] = useState<LoginMethod>("email");
  const [resetMethod, setResetMethod] = useState<ResetMethod>("email");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [resetEmailSent, setResetEmailSent] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [passwordUpdated, setPasswordUpdated] = useState(false);
  const [phoneForReset, setPhoneForReset] = useState("");
  const [resendCooldown, setResendCooldown] = useState(0);
  const [resendLoading, setResendLoading] = useState(false);
  const navigate = useNavigate();
  const { toast } = useToast();

  const [authForm, setAuthForm] = useState({
    email: "",
    password: "",
    fullName: "",
    phone: "",
    confirmPassword: "",
    otp: "",
  });

  // Client-side rate limiting state
  const [failedAttempts, setFailedAttempts] = useState(0);
  const [lockoutUntil, setLockoutUntil] = useState<number | null>(null);
  const [lockoutCountdown, setLockoutCountdown] = useState(0);

  // Session tracking
  const trackSession = useTrackSession();

  // Cooldown timer effect
  useEffect(() => {
    if (resendCooldown > 0) {
      const timer = setTimeout(() => setResendCooldown(resendCooldown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [resendCooldown]);

  // Lockout countdown timer
  useEffect(() => {
    if (lockoutUntil && lockoutUntil > Date.now()) {
      const interval = setInterval(() => {
        const remaining = Math.ceil((lockoutUntil - Date.now()) / 1000);
        if (remaining <= 0) {
          setLockoutUntil(null);
          setLockoutCountdown(0);
          clearInterval(interval);
        } else {
          setLockoutCountdown(remaining);
        }
      }, 1000);
      return () => clearInterval(interval);
    }
  }, [lockoutUntil]);

  // Check for password recovery token in URL
  useEffect(() => {
    const hashParams = new URLSearchParams(window.location.hash.substring(1));
    const accessToken = hashParams.get('access_token');
    const type = hashParams.get('type');
    
    if (type === 'recovery' && accessToken) {
      setView("update-password");
    }

    // Listen for auth state changes to detect recovery
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'PASSWORD_RECOVERY') {
        setView("update-password");
      }
    });

    return () => subscription.unsubscribe();
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    // Client-side rate limit check
    if (lockoutUntil && lockoutUntil > Date.now()) {
      toast({
        title: "Too many attempts",
        description: `Please wait ${lockoutCountdown} seconds before trying again.`,
        variant: "destructive",
      });
      return;
    }

    setLoading(true);

    try {
      if (loginMethod === "email") {
        const validated = authSchema.parse(authForm);
        
        const { error } = await supabase.auth.signInWithPassword({
          email: validated.email,
          password: validated.password,
        });

        if (error) {
          setFailedAttempts(prev => {
            const newCount = prev + 1;
            if (newCount >= 5) {
              setLockoutUntil(Date.now() + 30000);
              setLockoutCountdown(30);
            } else if (newCount >= 3) {
              setLockoutUntil(Date.now() + 10000);
              setLockoutCountdown(10);
            }
            return newCount;
          });
          toast({
            title: "Login failed",
            description: error.message,
            variant: "destructive",
          });
        } else {
          setFailedAttempts(0);
          setLockoutUntil(null);
          localStorage.removeItem("guestMode");
          // Track the session
          trackSession.mutate();
          toast({
            title: "Success",
            description: "You have been logged in successfully",
          });
          navigate("/");
        }
      } else {
        // Phone login via edge function
        const validated = phoneLoginSchema.parse(authForm);
        
        const response = await supabase.functions.invoke('login-with-phone', {
          body: { 
            phone: validated.phone, 
            password: validated.password 
          },
        });

        const { data, error } = response;
        
        if (error) {
          // Parse the error body if it's a FunctionsHttpError
          let errorMessage = "Invalid credentials";
          try {
            // The error context may contain the response body
            if (error.context?.body) {
              const errorBody = JSON.parse(error.context.body);
              errorMessage = errorBody.error || errorMessage;
            }
          } catch {
            errorMessage = error.message || errorMessage;
          }
          
          toast({
            title: "Login failed",
            description: errorMessage,
            variant: "destructive",
          });
        } else if (data?.error) {
          toast({
            title: "Login failed",
            description: data.error,
            variant: "destructive",
          });
        } else if (data?.session) {
          setFailedAttempts(0);
          setLockoutUntil(null);
          // Set the session manually
          await supabase.auth.setSession({
            access_token: data.session.access_token,
            refresh_token: data.session.refresh_token,
          });
          
          localStorage.removeItem("guestMode");
          // Track the session
          trackSession.mutate();
          toast({
            title: "Success",
            description: "You have been logged in successfully",
          });
          navigate("/");
        }
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        toast({
          title: "Validation error",
          description: error.errors[0].message,
          variant: "destructive",
        });
      } else {
        setFailedAttempts(prev => {
          const newCount = prev + 1;
          if (newCount >= 5) {
            setLockoutUntil(Date.now() + 30000);
            setLockoutCountdown(30);
          } else if (newCount >= 3) {
            setLockoutUntil(Date.now() + 10000);
            setLockoutCountdown(10);
          }
          return newCount;
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const validated = signupSchema.parse(authForm);
      const redirectUrl = `${window.location.origin}/`;
      
      const { data, error } = await supabase.auth.signUp({
        email: validated.email,
        password: validated.password,
        options: {
          emailRedirectTo: redirectUrl,
          data: {
            full_name: validated.fullName,
            phone: validated.phone,
            username: `user_${Date.now()}`,
          },
        },
      });

      if (error) {
        if (error.message.includes("already registered")) {
          toast({
            title: "Account exists",
            description: "This email is already registered. Please sign in instead.",
            variant: "destructive",
          });
        } else {
          toast({
            title: "Sign up failed",
            description: error.message,
            variant: "destructive",
          });
        }
      } else if (data?.user) {
        localStorage.removeItem("guestMode");
        toast({
          title: "Success",
          description: "Account created successfully! You can now sign in.",
        });
        setView("login");
        setAuthForm({ email: validated.email, password: "", fullName: "", phone: "", confirmPassword: "", otp: "" });
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        toast({
          title: "Validation error",
          description: error.errors[0].message,
          variant: "destructive",
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleForgotPasswordEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const validated = forgotPasswordEmailSchema.parse({ email: authForm.email });
      const redirectUrl = `${window.location.origin}/auth`;
      
      const { error } = await supabase.auth.resetPasswordForEmail(validated.email, {
        redirectTo: redirectUrl,
      });

      if (error) {
        toast({
          title: "Failed to send reset email",
          description: error.message,
          variant: "destructive",
        });
      } else {
        setResetEmailSent(true);
        toast({
          title: "Reset email sent",
          description: "Check your inbox for the password reset link.",
        });
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        toast({
          title: "Validation error",
          description: error.errors[0].message,
          variant: "destructive",
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleForgotPasswordPhone = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const validated = forgotPasswordPhoneSchema.parse({ phone: authForm.phone });
      
      const { data, error } = await supabase.functions.invoke('send-otp', {
        body: { phone: validated.phone },
      });

      if (error) {
        toast({
          title: "Failed to send OTP",
          description: error.message || "Please try again later.",
          variant: "destructive",
        });
      } else {
        setPhoneForReset(validated.phone);
        setOtpSent(true);
        setResendCooldown(OTP_COOLDOWN_SECONDS);
        setView("verify-otp");
        toast({
          title: "OTP sent",
          description: "Check your phone for the verification code.",
        });
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        toast({
          title: "Validation error",
          description: error.errors[0].message,
          variant: "destructive",
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    if (resendCooldown > 0 || !phoneForReset) return;
    
    setResendLoading(true);
    try {
      const { data, error } = await supabase.functions.invoke('send-otp', {
        body: { phone: phoneForReset },
      });

      if (error || data?.error) {
        toast({
          title: "Failed to resend OTP",
          description: data?.error || error?.message || "Please try again later.",
          variant: "destructive",
        });
      } else {
        setResendCooldown(OTP_COOLDOWN_SECONDS);
        setAuthForm({ ...authForm, otp: "" });
        toast({
          title: "OTP resent",
          description: "A new verification code has been sent to your phone.",
        });
      }
    } catch (error) {
      toast({
        title: "Failed to resend OTP",
        description: "An unexpected error occurred.",
        variant: "destructive",
      });
    } finally {
      setResendLoading(false);
    }
  };

  const handleVerifyOtpAndResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const validated = otpSchema.parse({
        otp: authForm.otp,
        password: authForm.password,
        confirmPassword: authForm.confirmPassword,
      });

      const { data, error } = await supabase.functions.invoke('verify-otp', {
        body: {
          phone: phoneForReset,
          otp: validated.otp,
          newPassword: validated.password,
        },
      });

      if (error || data?.error) {
        toast({
          title: "Verification failed",
          description: data?.error || error?.message || "Invalid or expired OTP",
          variant: "destructive",
        });
      } else {
        setPasswordUpdated(true);
        toast({
          title: "Password updated",
          description: "Your password has been updated successfully.",
        });
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        toast({
          title: "Validation error",
          description: error.errors[0].message,
          variant: "destructive",
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleUpdatePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    try {
      const validated = updatePasswordSchema.parse({
        password: authForm.password,
        confirmPassword: authForm.confirmPassword,
      });

      const { error } = await supabase.auth.updateUser({
        password: validated.password,
      });

      if (error) {
        toast({
          title: "Failed to update password",
          description: error.message,
          variant: "destructive",
        });
      } else {
        setPasswordUpdated(true);
        toast({
          title: "Password updated",
          description: "Your password has been updated successfully.",
        });
      }
    } catch (error) {
      if (error instanceof z.ZodError) {
        toast({
          title: "Validation error",
          description: error.errors[0].message,
          variant: "destructive",
        });
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSocialLogin = async (provider: 'google' | 'github') => {
    setLoading(true);
    try {
      const { error } = await supabase.auth.signInWithOAuth({
        provider,
        options: {
          redirectTo: `${window.location.origin}/`,
        },
      });

      if (error) {
        toast({
          title: "Login failed",
          description: error.message,
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Login failed",
        description: "An unexpected error occurred",
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const handleGuestMode = () => {
    localStorage.setItem("guestMode", "true");
    toast({
      title: "Guest mode activated",
      description: "You can browse as a guest. Create an account anytime!",
    });
    navigate("/");
  };

  const resetForm = () => {
    setAuthForm({ email: "", password: "", fullName: "", phone: "", confirmPassword: "", otp: "" });
    setResetEmailSent(false);
    setOtpSent(false);
    setPasswordUpdated(false);
    setPhoneForReset("");
  };

  const getHeaderText = () => {
    switch (view) {
      case "login":
        return "Welcome back!";
      case "signup":
        return "Create Your Account";
      case "forgot-password":
        return "Reset Password";
      case "update-password":
        return "Set New Password";
      case "verify-otp":
        return "Verify OTP";
    }
  };

  const renderVerifyOtpForm = () => {
    if (passwordUpdated) {
      return (
        <div className="text-center py-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-100 dark:bg-green-900 mb-4">
            <CheckCircle className="w-8 h-8 text-green-600 dark:text-green-400" />
          </div>
          <h3 className="text-lg font-semibold text-foreground mb-2">Password Updated!</h3>
          <p className="text-muted-foreground mb-6">
            Your password has been successfully updated. You can now sign in with your new password.
          </p>
          <Button
            onClick={() => {
              setView("login");
              resetForm();
            }}
            className="w-full"
          >
            Continue to Sign In
          </Button>
        </div>
      );
    }

    return (
      <form onSubmit={handleVerifyOtpAndResetPassword} className="space-y-4">
        <div className="text-center mb-4">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-primary/10 mb-3">
            <Smartphone className="w-6 h-6 text-primary" />
          </div>
          <p className="text-sm text-muted-foreground">
            Enter the 6-digit code sent to<br />
            <span className="font-medium text-foreground">{phoneForReset}</span>
          </p>
        </div>
        <div className="space-y-2">
          <Label htmlFor="otp" className="text-card-foreground">
            Verification Code <span className="text-destructive">*</span>
          </Label>
          <Input
            id="otp"
            type="text"
            placeholder="123456"
            value={authForm.otp}
            onChange={(e) => setAuthForm({ ...authForm, otp: e.target.value.replace(/\D/g, '').slice(0, 6) })}
            required
            maxLength={6}
            className="bg-background text-center text-2xl tracking-widest"
          />
          <div className="flex items-center justify-center mt-2">
            {resendCooldown > 0 ? (
              <p className="text-sm text-muted-foreground">
                Resend code in <span className="font-medium text-foreground">{resendCooldown}s</span>
              </p>
            ) : (
              <button
                type="button"
                onClick={handleResendOtp}
                disabled={resendLoading}
                className="text-sm text-primary hover:underline disabled:opacity-50"
              >
                {resendLoading ? "Sending..." : "Didn't receive code? Resend"}
              </button>
            )}
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="new-password-otp" className="text-card-foreground">
            New Password <span className="text-destructive">*</span>
          </Label>
          <div className="relative">
            <Input
              id="new-password-otp"
              type={showPassword ? "text" : "password"}
              placeholder="••••••••"
              value={authForm.password}
              onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })}
              required
              className="bg-background pr-10"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirm-new-password-otp" className="text-card-foreground">
            Confirm New Password <span className="text-destructive">*</span>
          </Label>
          <Input
            id="confirm-new-password-otp"
            type={showPassword ? "text" : "password"}
            placeholder="••••••••"
            value={authForm.confirmPassword}
            onChange={(e) => setAuthForm({ ...authForm, confirmPassword: e.target.value })}
            required
            className="bg-background"
          />
        </div>
        <Button type="submit" className="w-full" disabled={loading}>
          {loading ? "Verifying..." : "Reset Password"}
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={() => {
            setView("forgot-password");
            setOtpSent(false);
          }}
          className="w-full"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back
        </Button>
      </form>
    );
  };

  const renderUpdatePasswordForm = () => {
    if (passwordUpdated) {
      return (
        <div className="text-center py-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-100 dark:bg-green-900 mb-4">
            <CheckCircle className="w-8 h-8 text-green-600 dark:text-green-400" />
          </div>
          <h3 className="text-lg font-semibold text-foreground mb-2">Password Updated!</h3>
          <p className="text-muted-foreground mb-6">
            Your password has been successfully updated. You can now sign in with your new password.
          </p>
          <Button
            onClick={() => {
              setView("login");
              resetForm();
              // Clear hash from URL
              window.history.replaceState(null, '', window.location.pathname);
            }}
            className="w-full"
          >
            Continue to Sign In
          </Button>
        </div>
      );
    }

    return (
      <form onSubmit={handleUpdatePassword} className="space-y-4">
        <div className="text-center mb-4">
          <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-primary/10 mb-3">
            <KeyRound className="w-6 h-6 text-primary" />
          </div>
          <p className="text-sm text-muted-foreground">
            Enter your new password below.
          </p>
        </div>
        <div className="space-y-2">
          <Label htmlFor="new-password" className="text-card-foreground">
            New Password <span className="text-destructive">*</span>
          </Label>
          <div className="relative">
            <Input
              id="new-password"
              type={showPassword ? "text" : "password"}
              placeholder="••••••••"
              value={authForm.password}
              onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })}
              required
              className="bg-background pr-10"
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirm-new-password" className="text-card-foreground">
            Confirm New Password <span className="text-destructive">*</span>
          </Label>
          <Input
            id="confirm-new-password"
            type={showPassword ? "text" : "password"}
            placeholder="••••••••"
            value={authForm.confirmPassword}
            onChange={(e) => setAuthForm({ ...authForm, confirmPassword: e.target.value })}
            required
            className="bg-background"
          />
        </div>
        <Button type="submit" className="w-full" disabled={loading}>
          {loading ? "Updating..." : "Update Password"}
        </Button>
      </form>
    );
  };

  const renderForgotPasswordForm = () => {
    if (resetEmailSent) {
      return (
        <div className="text-center py-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-100 dark:bg-green-900 mb-4">
            <CheckCircle className="w-8 h-8 text-green-600 dark:text-green-400" />
          </div>
          <h3 className="text-lg font-semibold text-foreground mb-2">Check your email</h3>
          <p className="text-muted-foreground mb-6">
            We've sent a password reset link to<br />
            <span className="font-medium text-foreground">{authForm.email}</span>
          </p>
          <Button
            variant="outline"
            onClick={() => {
              setView("login");
              resetForm();
            }}
            className="w-full"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back to Sign In
          </Button>
        </div>
      );
    }

    return (
      <div className="space-y-4">
        {/* Reset Method Toggle */}
        <div className="flex rounded-lg border border-border p-1 bg-muted">
          <button
            type="button"
            onClick={() => setResetMethod("email")}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
              resetMethod === "email"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            <Mail className="w-4 h-4" />
            Email
          </button>
          <button
            type="button"
            onClick={() => setResetMethod("phone")}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
              resetMethod === "phone"
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            <Phone className="w-4 h-4" />
            Phone
          </button>
        </div>

        <form onSubmit={resetMethod === "email" ? handleForgotPasswordEmail : handleForgotPasswordPhone} className="space-y-4">
          <div className="text-center mb-4">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-primary/10 mb-3">
              {resetMethod === "email" ? (
                <Mail className="w-6 h-6 text-primary" />
              ) : (
                <Phone className="w-6 h-6 text-primary" />
              )}
            </div>
            <p className="text-sm text-muted-foreground">
              {resetMethod === "email"
                ? "Enter your email address and we'll send you a link to reset your password."
                : "Enter your phone number and we'll send you an OTP to reset your password."}
            </p>
          </div>

          {resetMethod === "email" ? (
            <div className="space-y-2">
              <Label htmlFor="reset-email" className="text-card-foreground">
                Email Address <span className="text-destructive">*</span>
              </Label>
              <Input
                id="reset-email"
                type="email"
                placeholder="you@example.com"
                value={authForm.email}
                onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })}
                required
                className="bg-background"
              />
            </div>
          ) : (
            <div className="space-y-2">
              <Label htmlFor="reset-phone" className="text-card-foreground">
                Phone Number <span className="text-destructive">*</span>
              </Label>
              <Input
                id="reset-phone"
                type="tel"
                placeholder="+1234567890"
                value={authForm.phone}
                onChange={(e) => setAuthForm({ ...authForm, phone: e.target.value })}
                required
                className="bg-background"
              />
              <p className="text-xs text-muted-foreground">
                Include country code (e.g., +1 for US)
              </p>
            </div>
          )}

          <Button type="submit" className="w-full" disabled={loading}>
            {loading ? "Sending..." : resetMethod === "email" ? "Send Reset Link" : "Send OTP"}
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => {
              setView("login");
              resetForm();
            }}
            className="w-full"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back to Sign In
          </Button>
        </form>
      </div>
    );
  };

  const renderSocialLoginButtons = () => (
    <div className="space-y-3">
      <div className="relative">
        <div className="absolute inset-0 flex items-center">
          <span className="w-full border-t" />
        </div>
        <div className="relative flex justify-center text-xs uppercase">
          <span className="bg-card px-2 text-muted-foreground">Or continue with</span>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Button
          type="button"
          variant="outline"
          onClick={() => handleSocialLogin('google')}
          disabled={loading}
          className="w-full"
        >
          <svg className="w-4 h-4 mr-2" viewBox="0 0 24 24">
            <path
              fill="currentColor"
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
            />
            <path
              fill="currentColor"
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            />
            <path
              fill="currentColor"
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
            />
            <path
              fill="currentColor"
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
            />
          </svg>
          Google
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => handleSocialLogin('github')}
          disabled={loading}
          className="w-full"
        >
          <svg className="w-4 h-4 mr-2" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
          </svg>
          GitHub
        </Button>
      </div>
    </div>
  );

  const renderLoginSignupForm = () => {
    const isLogin = view === "login";

    return (
      <>
        {/* Login Method Toggle (only for login) */}
        {isLogin && (
          <div className="flex rounded-lg border border-border p-1 bg-muted mb-4">
            <button
              type="button"
              onClick={() => setLoginMethod("email")}
              className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
                loginMethod === "email"
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Mail className="w-4 h-4" />
              Email
            </button>
            <button
              type="button"
              onClick={() => setLoginMethod("phone")}
              className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-md text-sm font-medium transition-colors ${
                loginMethod === "phone"
                  ? "bg-background text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Phone className="w-4 h-4" />
              Phone
            </button>
          </div>
        )}

        <form onSubmit={isLogin ? handleLogin : handleSignup} className="space-y-4">
          {!isLogin && (
            <>
              <div className="space-y-2">
                <Label htmlFor="auth-fullname" className="text-card-foreground">
                  Full Name <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="auth-fullname"
                  type="text"
                  placeholder="John Doe"
                  value={authForm.fullName}
                  onChange={(e) => setAuthForm({ ...authForm, fullName: e.target.value })}
                  required
                  className="bg-background"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="auth-phone" className="text-card-foreground">
                  Phone Number <span className="text-destructive">*</span>
                </Label>
                <Input
                  id="auth-phone"
                  type="tel"
                  placeholder="+1234567890"
                  value={authForm.phone}
                  onChange={(e) => setAuthForm({ ...authForm, phone: e.target.value })}
                  required
                  className="bg-background"
                />
                <p className="text-xs text-muted-foreground">
                  Include country code (e.g., +1 for US)
                </p>
              </div>
            </>
          )}

          {/* Email or Phone field based on login method */}
          {isLogin && loginMethod === "phone" ? (
            <div className="space-y-2">
              <Label htmlFor="auth-phone-login" className="text-card-foreground">
                Phone Number <span className="text-destructive">*</span>
              </Label>
              <Input
                id="auth-phone-login"
                type="tel"
                placeholder="+1234567890"
                value={authForm.phone}
                onChange={(e) => setAuthForm({ ...authForm, phone: e.target.value })}
                required
                className="bg-background"
              />
              <p className="text-xs text-muted-foreground">
                Include country code (e.g., +1 for US)
              </p>
            </div>
          ) : (
            <div className="space-y-2">
              <Label htmlFor="auth-email" className="text-card-foreground">
                Email Address <span className="text-destructive">*</span>
              </Label>
              <Input
                id="auth-email"
                type="email"
                placeholder="you@example.com"
                value={authForm.email}
                onChange={(e) => setAuthForm({ ...authForm, email: e.target.value })}
                required
                className="bg-background"
              />
            </div>
          )}

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label htmlFor="auth-password" className="text-card-foreground">
                Password <span className="text-destructive">*</span>
              </Label>
              {isLogin && (
                <button
                  type="button"
                  onClick={() => {
                    setView("forgot-password");
                    resetForm();
                  }}
                  className="text-xs text-primary hover:underline"
                >
                  Forgot password?
                </button>
              )}
            </div>
            <div className="relative">
              <Input
                id="auth-password"
                type={showPassword ? "text" : "password"}
                placeholder="••••••••"
                value={authForm.password}
                onChange={(e) => setAuthForm({ ...authForm, password: e.target.value })}
                required
                className="bg-background pr-10"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>
          {!isLogin && (
            <div className="space-y-2">
              <Label htmlFor="auth-confirm-password" className="text-card-foreground">
                Confirm Password <span className="text-destructive">*</span>
              </Label>
              <div className="relative">
                <Input
                  id="auth-confirm-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="••••••••"
                  value={authForm.confirmPassword}
                  onChange={(e) => setAuthForm({ ...authForm, confirmPassword: e.target.value })}
                  required
                  className="bg-background pr-10"
                />
              </div>
            </div>
          )}
          <Button type="submit" className="w-full" disabled={loading || (isLogin && lockoutUntil !== null && lockoutUntil > Date.now())}>
            {isLogin && lockoutUntil && lockoutUntil > Date.now()
              ? `Locked out (${lockoutCountdown}s)`
              : loading
                ? (isLogin ? "Signing in..." : "Creating account...")
                : (isLogin ? "Sign In" : "Create Account")}
          </Button>
          {isLogin && failedAttempts >= 2 && !lockoutUntil && (
            <div className="flex items-center gap-2 text-sm text-amber-600 dark:text-amber-400 mt-2">
              <ShieldAlert className="w-4 h-4 shrink-0" />
              <span>{5 - failedAttempts} attempt{5 - failedAttempts !== 1 ? 's' : ''} remaining before temporary lockout</span>
            </div>
          )}
          {isLogin && lockoutUntil && lockoutUntil > Date.now() && (
            <div className="flex items-center gap-2 text-sm text-destructive mt-2">
              <ShieldAlert className="w-4 h-4 shrink-0" />
              <span>Too many failed attempts. Wait {lockoutCountdown}s before trying again.</span>
            </div>
          )}
        </form>

        {/* Social Login Buttons */}
        <div className="mt-4">
          {renderSocialLoginButtons()}
        </div>

        {/* Toggle Login/Signup */}
        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={() => {
              setView(isLogin ? "signup" : "login");
              resetForm();
            }}
            className="text-sm text-primary hover:underline"
          >
            {isLogin ? "Don't have an account? Sign up" : "Already have an account? Sign in"}
          </button>
        </div>

        {/* Guest Mode Button */}
        <div className="mt-4">
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <span className="w-full border-t" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-card px-2 text-muted-foreground">Or</span>
            </div>
          </div>
          <Button
            type="button"
            variant="outline"
            className="w-full mt-4"
            onClick={handleGuestMode}
          >
            <UserCircle className="w-4 h-4 mr-2" />
            Continue as Guest
          </Button>
        </div>
      </>
    );
  };

  const renderFormContent = () => {
    switch (view) {
      case "forgot-password":
        return renderForgotPasswordForm();
      case "update-password":
        return renderUpdatePasswordForm();
      case "verify-otp":
        return renderVerifyOtpForm();
      default:
        return renderLoginSignupForm();
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <div className="w-full max-w-md">
        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary mb-4">
            <MessageSquare className="w-8 h-8 text-primary-foreground" />
          </div>
          <h1 className="text-3xl font-bold text-foreground mb-2">Loop</h1>
          <p className="text-muted-foreground">{getHeaderText()}</p>
        </div>

        {/* Main Card */}
        <div className="bg-card rounded-lg border border-border shadow-lg overflow-hidden">
          {/* Status Badge */}
          <div className="bg-muted px-6 py-3 flex items-center justify-between border-b border-border">
            {view === "login" ? (
              <>
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <WifiOff className="w-4 h-4" />
                  <span>Disconnected</span>
                </div>
                <div className="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
                  <ShieldCheck className="w-4 h-4" />
                  <span>SSL Secured</span>
                </div>
              </>
            ) : (
              <div className="flex items-center gap-2 text-sm text-green-600 dark:text-green-400 mx-auto">
                <ShieldCheck className="w-4 h-4" />
                <span>
                  {view === "signup" ? "Secure Registration" : 
                   view === "update-password" ? "Secure Password Update" : 
                   view === "verify-otp" ? "Secure OTP Verification" : "Secure Reset"}
                </span>
              </div>
            )}
          </div>

          {/* Form Content */}
          <div className="p-6">
            {renderFormContent()}
          </div>
        </div>

        {/* Footer */}
        <div className="mt-6 text-center">
          <p className="text-sm text-muted-foreground">
            Authentication is optional. Use guest mode to try Loop!
          </p>
        </div>
      </div>
    </div>
  );
};

export default Auth;
