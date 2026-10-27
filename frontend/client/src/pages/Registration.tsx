import { useEffect } from "react";
import RegistrationForm from "@/components/registration/RegistrationForm";
import { Particles } from "@modl-gg/shared-web/components/ui/particles";
import { Toaster } from "@modl-gg/shared-web/components/ui/toaster";
import './registration.css';

export default function Registration() {
  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  return (
    <div className="registration-page bg-background text-foreground min-h-screen relative overflow-x-hidden">
      <div className="fixed inset-0 pointer-events-none opacity-20 z-0">
        <Particles className="absolute inset-0" quantity={20} />
      </div>
      <RegistrationForm />
      <Toaster />
    </div>
  );
}
