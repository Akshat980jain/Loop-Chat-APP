import { useState, useEffect } from "react";
import loopLogo from "@/assets/loop-logo.png";

interface SplashScreenProps {
  onComplete: () => void;
  minDuration?: number;
}

export const SplashScreen = ({ onComplete, minDuration = 2000 }: SplashScreenProps) => {
  const [isVisible, setIsVisible] = useState(true);
  const [isFading, setIsFading] = useState(false);

  useEffect(() => {
    const fadeTimer = setTimeout(() => {
      setIsFading(true);
    }, minDuration);

    const completeTimer = setTimeout(() => {
      setIsVisible(false);
      onComplete();
    }, minDuration + 500);

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(completeTimer);
    };
  }, [minDuration, onComplete]);

  if (!isVisible) return null;

  return (
    <div
      className={`fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-gradient-to-br from-cyan-500 via-teal-500 to-blue-600 transition-opacity duration-500 ${
        isFading ? "opacity-0" : "opacity-100"
      }`}
    >
      <div className="flex flex-col items-center gap-6 animate-fade-in">
        <div className="relative">
          <img
            src={loopLogo}
            alt="Loop Logo"
            className="w-32 h-32 rounded-3xl shadow-2xl animate-scale-in"
          />
          <div className="absolute inset-0 rounded-3xl bg-white/20 animate-pulse" />
        </div>
        
        <div className="flex flex-col items-center gap-2">
          <h1 className="text-4xl font-bold text-white tracking-wide animate-slide-up">
            Loop
          </h1>
          <p className="text-white/80 text-sm animate-slide-up animation-delay-200">
            Stay Connected
          </p>
        </div>

        <div className="mt-8 flex gap-1">
          <div className="w-2 h-2 rounded-full bg-white/80 animate-bounce" style={{ animationDelay: "0ms" }} />
          <div className="w-2 h-2 rounded-full bg-white/80 animate-bounce" style={{ animationDelay: "150ms" }} />
          <div className="w-2 h-2 rounded-full bg-white/80 animate-bounce" style={{ animationDelay: "300ms" }} />
        </div>
      </div>
    </div>
  );
};
