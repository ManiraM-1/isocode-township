package com.isocode.township.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.isocode.township.auth.DemoUsersController.DemoUser;
import com.isocode.township.auth.dto.MeResponse;
import com.isocode.township.auth.dto.MeResponse.FlatMembership;
import com.isocode.township.complaint.ComplaintCategory;
import com.isocode.township.flat.MemberRelation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoUsersControllerTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final MeService meService = mock(MeService.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AppUserRepository.class, () -> userRepository)
            .withBean(MeService.class, () -> meService)
            .withUserConfiguration(DemoUsersController.class);

    @Test
    void theDemoUserListExistsOnlyInDemoMode() {
        runner.withPropertyValues("app.demo-mode=true")
                .run(context -> assertThat(context).hasSingleBean(DemoUsersController.class));
        runner.withPropertyValues("app.demo-mode=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoUsersController.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoUsersController.class));
    }

    @Test
    void listsResidentsWithTheirFlatAndStaffWithout() {
        Instant now = Instant.parse("2026-11-03T04:30:00Z");
        when(userRepository.findByPhone(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+919000000002"))
                .thenReturn(Optional.of(AppUser.resident("+919000000002", "Swati Joshi", now)));
        when(userRepository.findByPhone("+919000000101"))
                .thenReturn(Optional.of(AppUser.staff("+919000000101", "Anjali Deshmukh",
                        Set.of(ComplaintCategory.PLUMBING), false, now)));
        when(meService.describe(any())).thenReturn(new MeResponse(null, "", Role.RESIDENT, false,
                List.of(new FlatMembership(1L, "T12-0408", MemberRelation.TENANT))))
                .thenReturn(new MeResponse(null, "", Role.HELPDESK, false, List.of()));

        List<DemoUser> users = new DemoUsersController(userRepository, meService).demoUsers();

        assertThat(users).extracting(DemoUser::name, DemoUser::role, DemoUser::flatCode)
                .containsExactly(tuple("Swati Joshi", Role.RESIDENT, "T12-0408"),
                        tuple("Anjali Deshmukh", Role.HELPDESK, null));
    }
}
