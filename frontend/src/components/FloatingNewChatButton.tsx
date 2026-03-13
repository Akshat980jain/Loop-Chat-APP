import { Plus } from "lucide-react";
import { Button } from "./ui/button";
import { cn } from "@/lib/utils";
import { useHaptics } from "@/hooks/useHaptics";

type FloatingNewChatButtonProps = {
  visible: boolean;
  onClick: () => void;
};

export const FloatingNewChatButton = ({ visible, onClick }: FloatingNewChatButtonProps) => {
  const haptics = useHaptics();

  const handleClick = () => {
    haptics.medium();
    onClick();
  };

  return (
    <Button
      onClick={handleClick}
      className={cn(
        "fixed bottom-20 right-6 h-14 w-14 rounded-full shadow-lg bg-primary hover:bg-primary/90 text-primary-foreground transition-all duration-300 md:hidden z-40",
        visible ? "translate-y-0 opacity-100 scale-100" : "translate-y-16 opacity-0 scale-75 pointer-events-none"
      )}
      size="icon"
    >
      <Plus className="w-6 h-6" />
    </Button>
  );
};
