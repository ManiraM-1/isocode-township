package com.isocode.township.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

class SafetyRuleTest {

    private static SafetyRule rule;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadTheConfiguredTerms() throws IOException {
        try (InputStream config = SafetyRuleTest.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> yaml = new Yaml().load(config);
            Map<String, Object> app = (Map<String, Object>) yaml.get("app");
            Map<String, Object> safety = (Map<String, Object>) app.get("safety");
            rule = new SafetyRule((List<String>) safety.get("terms"));
        }
    }

    @Test
    void englishSafetyWordsAreFoundInAnyCase() {
        assertThat(rule.check("There is a GAS LEAK near the stairs")).contains("gas", "gas leak");
        assertThat(rule.check("Our lift stuck between floors 8 and 9")).contains("lift stuck");
        assertThat(rule.check("Sparks are coming from the meter box")).contains("sparks");
        assertThat(rule.check("My son got an electric shock from the switch")).contains("shock", "electric shock");
    }

    @Test
    void wholeWordsOnlySoFireplaceIsNotFireButFireIs() {
        assertThat(rule.check("The fireplace in the clubhouse is dirty")).isEmpty();
        assertThat(rule.check("Fire in the meter room on the ground floor")).containsExactly("fire");
        assertThat(rule.check("The gasket of the washing machine is torn")).isEmpty();
        assertThat(rule.check("Lights are currently off in the lobby")).isEmpty();
    }

    @Test
    void negationsStillEscalateBecauseAFalseAlarmIsCheaper() {
        assertThat(rule.check("No fire, but the smell is strange")).contains("fire");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "रसोई में गैस की बदबू आ रही है",
            "बच्चे लिफ्ट में फंसे हैं, जल्दी आइए",
            "मीटर से चिंगारी निकल रही है",
            "स्विच छूते ही करंट लगा",
            "जिने में धुआं भर गया है"
    })
    void hindiSafetyWordsEscalate(String complaint) {
        assertThat(rule.check(complaint)).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "आम्ही लिफ्टमध्ये अडकलो आहोत",
            "स्वयंपाकघरात गॅसचा वास येत आहे",
            "मीटरमधून ठिणगी येत आहे",
            "पायऱ्यांवर धूर दिसत आहे",
            "तिसऱ्या मजल्यावर आगीचा वास"
    })
    void marathiSafetyWordsEscalateEvenWithEndingsAttached(String complaint) {
        assertThat(rule.check(complaint)).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Lift mein phas gaye hain, help",
            "Kitchen me aag lag gayi",
            "Switch se current laga",
            "Seedhi par dhuan hai"
    })
    void hinglishSafetyWordsEscalate(String complaint) {
        assertThat(rule.check(complaint)).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "गेट के आगे कचरा पड़ा है",
            "आगे वाली लाइट खराब है",
            "the fireplace",
            "gasket of the tap is broken",
            "car parked in front",
            "Please send the current month maintenance bill",
            "Floor polish is not sparkling after cleaning",
            "मेरे पैसे फंसे हैं, रसीद नहीं मिली",
            "माझे पैसे अडकले आहेत",
            "पेमेंट फंस गया है, अभी तक नहीं दिखा",
            "कल धुआंधार बारिश से पार्किंग में पानी भर गया",
            "Lift band hogi kal 10 baje maintenance ke liye",
            "Lift jhatka deti hai rukte waqt",
            "Bill dekh ke bada jhatka laga"
    })
    void commonWordsThatLookLikeSafetyWordsDoNotEscalate(String complaint) {
        assertThat(rule.check(complaint)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "रसोई में आग लगी है",
            "मीटर बॉक्स में आग लग गई",
            "इमारतीला आगीमुळे धोका आहे",
            "आगीत सामान जळाले",
            "हम फंस गए हैं, दरवाज़ा नहीं खुल रहा",
            "मैं बाथरूम में फँस गई हूं",
            "बिजली का झटका लगा",
            "Getting current from the geyser switch",
            "Lift atak gayi hai 5th floor pe",
            "Hum phas gaye hain stairs ke door mein"
    })
    void explicitFormsStillEscalate(String complaint) {
        assertThat(rule.check(complaint)).isNotEmpty();
    }

    @Test
    void everydayComplaintsDoNotEscalate() {
        assertThat(rule.check("Kitchen sink drain is blocked")).isEmpty();
        assertThat(rule.check("पानी नहीं आ रहा है सुबह से")).isEmpty();
        assertThat(rule.check("Garbage was not collected for two days")).isEmpty();
    }
}
