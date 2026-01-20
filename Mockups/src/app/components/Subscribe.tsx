import { useState } from "react";
import { Card } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Check, Crown, Zap, Target, Video, TrendingUp, Award } from "lucide-react";

interface SubscribeProps {
  onNavigateBack: () => void;
}

type Plan = "monthly" | "quarterly" | "yearly";

export function Subscribe({ onNavigateBack }: SubscribeProps) {
  const [selectedPlan, setSelectedPlan] = useState<Plan>("quarterly");

  const plans = [
    {
      id: "monthly" as Plan,
      name: "Monthly",
      duration: "1 month",
      price: 9.99,
      pricePerMonth: 9.99,
      savings: null,
      badge: null,
    },
    {
      id: "quarterly" as Plan,
      name: "Quarterly",
      duration: "3 months",
      price: 24.99,
      pricePerMonth: 8.33,
      savings: "Save 17%",
      badge: "Popular",
    },
    {
      id: "yearly" as Plan,
      name: "Yearly",
      duration: "12 months",
      price: 79.99,
      pricePerMonth: 6.67,
      savings: "Save 33%",
      badge: "Best Value",
    },
  ];

  const features = [
    { icon: Video, text: "Unlimited AI coaching sessions" },
    { icon: Target, text: "Advanced pose analysis" },
    { icon: TrendingUp, text: "Detailed progress analytics" },
    { icon: Award, text: "Personalized training plans" },
    { icon: Zap, text: "Real-time feedback" },
    { icon: Check, text: "Access to all premium drills" },
  ];

  return (
    <div className="space-y-6 pb-6">
      {/* Header */}
      <div className="text-center">
        <div className="w-16 h-16 bg-gradient-to-br from-yellow-400 to-orange-500 rounded-full flex items-center justify-center mx-auto mb-4">
          <Crown className="w-8 h-8 text-white" />
        </div>
        <h1 className="text-2xl font-bold mb-2">Unlock Premium Features</h1>
        <p className="text-muted-foreground">
          Get unlimited access to AI coaching and advanced training tools
        </p>
      </div>

      {/* Plans */}
      <div className="space-y-3">
        {plans.map((plan) => (
          <Card
            key={plan.id}
            onClick={() => setSelectedPlan(plan.id)}
            className={`p-4 cursor-pointer transition-all ${
              selectedPlan === plan.id
                ? "border-2 border-blue-500 bg-blue-500/5"
                : "border-2 border-transparent hover:border-border"
            }`}
          >
            <div className="flex items-center justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <h3 className="font-semibold">{plan.name}</h3>
                  {plan.badge && (
                    <span className="text-xs bg-gradient-to-r from-orange-500 to-yellow-500 text-white px-2 py-0.5 rounded-full">
                      {plan.badge}
                    </span>
                  )}
                </div>
                <p className="text-sm text-muted-foreground mb-2">
                  {plan.duration}
                </p>
                <div className="flex items-baseline gap-2">
                  <span className="text-2xl font-bold">${plan.price}</span>
                  {plan.pricePerMonth !== plan.price && (
                    <span className="text-sm text-muted-foreground">
                      (${plan.pricePerMonth.toFixed(2)}/month)
                    </span>
                  )}
                </div>
                {plan.savings && (
                  <div className="mt-2">
                    <span className="text-xs bg-green-500/10 text-green-600 dark:text-green-400 px-2 py-1 rounded">
                      {plan.savings}
                    </span>
                  </div>
                )}
              </div>
              <div
                className={`w-6 h-6 rounded-full border-2 flex items-center justify-center ${
                  selectedPlan === plan.id
                    ? "border-blue-500 bg-blue-500"
                    : "border-muted-foreground"
                }`}
              >
                {selectedPlan === plan.id && (
                  <Check className="w-4 h-4 text-white" />
                )}
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Features */}
      <Card className="p-5">
        <h3 className="font-semibold mb-4">What's Included</h3>
        <div className="space-y-3">
          {features.map((feature, index) => (
            <div key={index} className="flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-blue-500/10 flex items-center justify-center flex-shrink-0">
                <feature.icon className="w-4 h-4 text-blue-500" />
              </div>
              <span className="text-sm">{feature.text}</span>
            </div>
          ))}
        </div>
      </Card>

      {/* CTA */}
      <div className="space-y-3">
        <Button className="w-full h-12 text-base bg-gradient-to-r from-blue-500 to-purple-500 hover:from-blue-600 hover:to-purple-600">
          <Crown className="w-5 h-5 mr-2" />
          Start {plans.find((p) => p.id === selectedPlan)?.name} Plan
        </Button>
        <Button
          variant="outline"
          className="w-full"
          onClick={onNavigateBack}
        >
          Maybe Later
        </Button>
      </div>

      {/* Footer */}
      <div className="text-center text-xs text-muted-foreground space-y-1">
        <p>Auto-renews. Cancel anytime.</p>
        <p>30-day money-back guarantee</p>
      </div>
    </div>
  );
}
