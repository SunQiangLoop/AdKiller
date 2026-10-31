package cn.local.adskip;

/** Evidence summary only: never infer absence of ads or causal success from missing events. */
public final class DiagnosticSummary {
    private final String pkg;
    private boolean started, ended;
    private long firstTarget=-1;
    private long firstTargetCapturedMs=-1;
    private long firstTargetResolvedMs=-1;
    private int events, targets, clicks, rejects, competitionYields, present, absent;
    private String endReason="unknown";
    public DiagnosticSummary(String pkg){this.pkg=pkg;}
    public void accept(String kind,long elapsed,boolean accepted,String reason,String state){
        acceptDetailed(kind,elapsed,accepted,reason,state,"",-1,-1);
    }
    public void acceptDetailed(String kind,long elapsed,boolean accepted,String reason,String state,String decisionClass,long capturedMs,long resolvedMs){
        events++;
        if(kind.equals("SESSION_START"))started=true;
        if(kind.equals("SESSION_END")){ended=true;endReason=reason;}
        if((kind.equals("CANDIDATE")||kind.equals("VISUAL_MATCH")||kind.equals("OCR_RESULT"))&&accepted){
            targets++;
            if(firstTarget<0||elapsed<firstTarget)firstTarget=elapsed;
            if(capturedMs>=0&&(firstTargetCapturedMs<0||capturedMs<firstTargetCapturedMs))firstTargetCapturedMs=capturedMs;
            if(resolvedMs>=0&&(firstTargetResolvedMs<0||resolvedMs<firstTargetResolvedMs))firstTargetResolvedMs=resolvedMs;
        }
        if(kind.equals("CLICK_CLAIM"))clicks++;
        if(kind.equals("DECISION_REJECTED")||kind.equals("SCAN_DISCARDED")||kind.equals("CLICK_CLAIM_REJECTED")){
            if("COMPETITION_SETTLED".equals(decisionClass))competitionYields++;
            else rejects++;
        }
        if(kind.equals("VERIFY_OBSERVATION")){
            if(state.equals("button_present"))present++;
            if(state.equals("button_absent")||state.equals("window_changed"))absent++;
        }
    }
    public String describe(){
        return pkg+" events="+events+" firstTargetMs="+firstTarget
            +(firstTargetCapturedMs>=0?" firstTargetCapturedMs="+firstTargetCapturedMs+" firstTargetResolvedMs="+firstTargetResolvedMs:"")
            +" targetObservations="+targets+" clickAttempts="+clicks+" rejected="+rejects
            +(competitionYields>0?" competitionYields="+competitionYields:"")
            +" buttonPresent="+present+" buttonAbsent="+absent+" end="+endReason
            +" completeness="+(started&&ended?"start_and_end_observed":"partial_or_still_active")
            +" conclusion="+(clicks>0?"click_attempted_cause_unconfirmed":targets>0?"target_observed_no_recorded_click":"no_target_evidence_not_failure");
    }
}
