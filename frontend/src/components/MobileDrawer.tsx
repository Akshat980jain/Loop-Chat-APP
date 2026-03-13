import { X } from "lucide-react";
import { Sidebar } from "./Sidebar";
import { cn } from "@/lib/utils";
import { useEffect } from "react";

type MobileDrawerProps = {
  isOpen: boolean;
  onClose: () => void;
  isGuest?: boolean;
};

export const MobileDrawer = ({ isOpen, onClose, isGuest = false }: MobileDrawerProps) => {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "unset";
    }
    return () => {
      document.body.style.overflow = "unset";
    };
  }, [isOpen]);

  return (
    <>
      {/* Backdrop */}
      <div
        className={cn(
          "fixed inset-0 bg-background/80 backdrop-blur-sm z-40 md:hidden transition-opacity duration-300",
          isOpen ? "opacity-100" : "opacity-0 pointer-events-none"
        )}
        onClick={onClose}
      />

      {/* Drawer */}
      <div
        className={cn(
          "fixed top-0 left-0 h-full w-80 z-50 md:hidden transition-transform duration-300 ease-out",
          isOpen ? "animate-slide-in-left" : "-translate-x-full"
        )}
      >
        <div className="relative h-full">
          <button
            onClick={onClose}
            className="absolute top-4 right-4 z-10 p-2 rounded-lg bg-muted hover:bg-muted/80 transition-colors"
          >
            <X className="w-5 h-5 text-foreground" />
          </button>
          <Sidebar isGuest={isGuest} />
        </div>
      </div>
    </>
  );
};
