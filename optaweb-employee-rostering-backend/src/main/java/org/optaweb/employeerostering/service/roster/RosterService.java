/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaweb.employeerostering.service.roster;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.validation.Validator;

import org.optaplanner.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import org.optaplanner.core.api.score.constraint.Indictment;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaweb.employeerostering.domain.employee.Employee;
import org.optaweb.employeerostering.domain.employee.EmployeeAvailability;
import org.optaweb.employeerostering.domain.employee.view.EmployeeAvailabilityView;
import org.optaweb.employeerostering.domain.roster.Pagination;
import org.optaweb.employeerostering.domain.roster.PublishResult;
import org.optaweb.employeerostering.domain.roster.Roster;
import org.optaweb.employeerostering.domain.roster.RosterState;
import org.optaweb.employeerostering.domain.roster.view.AvailabilityRosterView;
import org.optaweb.employeerostering.domain.roster.view.ShiftRosterView;
import org.optaweb.employeerostering.domain.rotation.TimeBucket;
import org.optaweb.employeerostering.domain.shift.Shift;
import org.optaweb.employeerostering.domain.shift.view.ShiftView;
import org.optaweb.employeerostering.domain.skill.Skill;
import org.optaweb.employeerostering.domain.spot.Spot;
import org.optaweb.employeerostering.service.common.AbstractRestService;
import org.optaweb.employeerostering.service.common.IndictmentUtils;
import org.optaweb.employeerostering.service.employee.EmployeeAvailabilityRepository;
import org.optaweb.employeerostering.service.employee.EmployeeRepository;
import org.optaweb.employeerostering.service.rotation.TimeBucketRepository;
import org.optaweb.employeerostering.service.shift.ShiftRepository;
import org.optaweb.employeerostering.service.skill.SkillRepository;
import org.optaweb.employeerostering.service.solver.SolverStatus;
import org.optaweb.employeerostering.service.solver.WannabeSolverManager;
import org.optaweb.employeerostering.service.spot.SpotRepository;
import org.optaweb.employeerostering.service.tenant.RosterConstraintConfigurationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RosterService extends AbstractRestService {

    private RosterStateRepository rosterStateRepository;
    private SkillRepository skillRepository;
    private SpotRepository spotRepository;
    private EmployeeRepository employeeRepository;
    private EmployeeAvailabilityRepository employeeAvailabilityRepository;
    private ShiftRepository shiftRepository;
    private RosterConstraintConfigurationRepository rosterConstraintConfigurationRepository;
    private TimeBucketRepository timeBucketRepository;

    private WannabeSolverManager solverManager;
    private IndictmentUtils indictmentUtils;

    public RosterService(Validator validator,
                         RosterStateRepository rosterStateRepository, SkillRepository skillRepository,
                         SpotRepository spotRepository, EmployeeRepository employeeRepository,
                         EmployeeAvailabilityRepository employeeAvailabilityRepository,
                         ShiftRepository shiftRepository,
                         RosterConstraintConfigurationRepository rosterConstraintConfigurationRepository,
                         TimeBucketRepository timeBucketRepository,
                         WannabeSolverManager solverManager, IndictmentUtils indictmentUtils) {
        super(validator);
        this.rosterStateRepository = rosterStateRepository;
        this.skillRepository = skillRepository;
        this.spotRepository = spotRepository;
        this.employeeRepository = employeeRepository;
        this.employeeAvailabilityRepository = employeeAvailabilityRepository;
        this.shiftRepository = shiftRepository;
        this.rosterConstraintConfigurationRepository = rosterConstraintConfigurationRepository;
        this.timeBucketRepository = timeBucketRepository;
        this.solverManager = solverManager;
        this.indictmentUtils = indictmentUtils;
    }

    // ************************************************************************
    // RosterState
    // ************************************************************************

    @Transactional
    public RosterState getRosterState(Integer tenantId) {
        RosterState rosterState = rosterStateRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("No RosterState entity found with tenantId (" +
                                                                       tenantId + ")."));
        validateBean(tenantId, rosterState);
        return rosterState;
    }

    // ************************************************************************
    // ShiftRosterView
    // ************************************************************************

    @Transactional
    public ShiftRosterView getCurrentShiftRosterView(Integer tenantId, Integer pageNumber,
                                                     Integer numberOfItemsPerPage) {
        RosterState rosterState = getRosterState(tenantId);
        LocalDate startDate = rosterState.getFirstPublishedDate();
        LocalDate endDate = rosterState.getFirstUnplannedDate();
        return getShiftRosterView(tenantId, startDate, endDate, Pagination.of(pageNumber, numberOfItemsPerPage));
    }

    @Transactional
    public ShiftRosterView getShiftRosterView(final Integer tenantId, Integer pageNumber, Integer numberOfItemsPerPage,
                                              final String startDateString,
                                              final String endDateString) {

        return getShiftRosterView(tenantId, LocalDate.parse(startDateString), LocalDate.parse(endDateString),
                                  Pagination.of(pageNumber, numberOfItemsPerPage));
    }

    private ShiftRosterView getShiftRosterView(final Integer tenantId,
                                               final LocalDate startDate,
                                               final LocalDate endDate,
                                               final Pagination pagination) {

        Pageable spotPage = PageRequest.of(pagination.getPageNumber(), pagination.getNumberOfItemsPerPage());
        final List<Spot> spots = spotRepository.findAllByTenantId(tenantId, spotPage);

        return getShiftRosterView(tenantId, startDate, endDate, spots);
    }

    @Transactional
    public ShiftRosterView getShiftRosterViewFor(Integer tenantId, String startDateString, String endDateString,
                                                 List<Spot> spotList) {
        LocalDate startDate = LocalDate.parse(startDateString);
        LocalDate endDate = LocalDate.parse(endDateString);
        if (spotList == null) {
            throw new IllegalArgumentException("The spotList (" + spotList + ") must not be null.");
        }

        return getShiftRosterView(tenantId, startDate, endDate, spotList);
    }

    private ShiftRosterView getShiftRosterView(Integer tenantId, LocalDate startDate, LocalDate endDate,
                                               List<Spot> spotList) {
        ShiftRosterView shiftRosterView = new ShiftRosterView(tenantId, startDate, endDate);
        shiftRosterView.setSpotList(spotList);
        List<Employee> employeeList = employeeRepository.findAllByTenantId(tenantId, PageRequest.of(0,
                                                                                                    Integer.MAX_VALUE));
        shiftRosterView.setEmployeeList(employeeList);

        Set<Spot> spotSet = new HashSet<>(spotList);
        ZoneId timeZone = getRosterState(tenantId).getTimeZone();

        List<Shift> shiftList = shiftRepository.filterWithSpots(tenantId, spotSet,
                                                                startDate.atStartOfDay(timeZone).toOffsetDateTime(),
                                                                endDate.atStartOfDay(timeZone).toOffsetDateTime());

        Map<Long, List<ShiftView>> spotIdToShiftViewListMap = new LinkedHashMap<>(spotList.size());
        // TODO FIXME race condition solverManager's bestSolution might differ from the one we just fetched, so the
        //  score might be inaccurate
        Roster roster = solverManager.getRoster(tenantId);
        if (roster == null || solverManager.getSolverStatus(tenantId) != SolverStatus.SOLVING) {
            roster = buildRoster(tenantId);
        }
        Map<Object, Indictment> indictmentMap = indictmentUtils.getIndictmentMapForRoster(roster);

        for (Shift shift : shiftList) {
            Indictment indictment = indictmentMap.get(shift);
            spotIdToShiftViewListMap.computeIfAbsent(shift.getSpot().getId(), k -> new ArrayList<>())
                    .add(indictmentUtils.getShiftViewWithIndictment(timeZone, shift, indictment));
        }
        shiftRosterView.setSpotIdToShiftViewListMap(spotIdToShiftViewListMap);

        shiftRosterView.setScore(roster == null ? null : roster.getScore());
        shiftRosterView.setRosterState(getRosterState(tenantId));
        shiftRosterView.setIndictmentSummary(indictmentUtils.getIndictmentSummaryForRoster(roster));

        return shiftRosterView;
    }

    // ************************************************************************
    // AvailabilityRosterView
    // ************************************************************************

    @Transactional
    public AvailabilityRosterView getCurrentAvailabilityRosterView(Integer tenantId,
                                                                   Integer pageNumber,
                                                                   Integer numberOfItemsPerPage) {
        RosterState rosterState = getRosterState(tenantId);
        LocalDate startDate = rosterState.getLastHistoricDate();
        LocalDate endDate = rosterState.getFirstUnplannedDate();
        return getAvailabilityRosterView(tenantId, startDate, endDate, Pagination.of(pageNumber, numberOfItemsPerPage));
    }

    @Transactional
    public AvailabilityRosterView getAvailabilityRosterView(Integer tenantId,
                                                            Integer pageNumber,
                                                            Integer numberOfItemsPerPage,
                                                            String startDateString,
                                                            String endDateString) {
        LocalDate startDate = LocalDate.parse(startDateString);
        LocalDate endDate = LocalDate.parse(endDateString);
        return getAvailabilityRosterView(tenantId, startDate, endDate, Pagination.of(pageNumber, numberOfItemsPerPage));
    }

    @Transactional
    public AvailabilityRosterView getAvailabilityRosterViewFor(Integer tenantId,
                                                               String startDateString,
                                                               String endDateString,
                                                               List<Employee> employeeList) {
        LocalDate startDate = LocalDate.parse(startDateString);
        LocalDate endDate = LocalDate.parse(endDateString);
        if (employeeList == null) {
            throw new IllegalArgumentException("The employeeList (" + employeeList + ") must not be null.");
        }
        return getAvailabilityRosterView(tenantId, startDate, endDate, employeeList);
    }

    private AvailabilityRosterView getAvailabilityRosterView(final Integer tenantId,
                                                             final LocalDate startDate,
                                                             final LocalDate endDate,
                                                             final Pagination pagination) {

        Pageable employeePage = PageRequest.of(pagination.getPageNumber(), pagination.getNumberOfItemsPerPage());
        final List<Employee> employeeList = employeeRepository.findAllByTenantId(tenantId, employeePage);

        return getAvailabilityRosterView(tenantId, startDate, endDate, employeeList);
    }

    private AvailabilityRosterView getAvailabilityRosterView(Integer tenantId,
                                                             LocalDate startDate,
                                                             LocalDate endDate,
                                                             List<Employee> employeeList) {
        AvailabilityRosterView availabilityRosterView = new AvailabilityRosterView(tenantId, startDate, endDate);
        List<Spot> spotList = spotRepository.findAllByTenantId(tenantId, PageRequest.of(0, Integer.MAX_VALUE));
        availabilityRosterView.setSpotList(spotList);

        availabilityRosterView.setEmployeeList(employeeList);

        Map<Long, List<ShiftView>> employeeIdToShiftViewListMap = new LinkedHashMap<>(employeeList.size());
        List<ShiftView> unassignedShiftViewList = new ArrayList<>();
        Set<Employee> employeeSet = new HashSet<>(employeeList);
        ZoneId timeZone = getRosterState(tenantId).getTimeZone();

        List<Shift> shiftList = shiftRepository.filterWithEmployees(tenantId, employeeSet,
                                                                    startDate.atStartOfDay(timeZone).toOffsetDateTime(),
                                                                    endDate.atStartOfDay(timeZone).toOffsetDateTime());

        Roster roster = solverManager.getRoster(tenantId);
        if (roster == null || solverManager.getSolverStatus(tenantId) != SolverStatus.SOLVING) {
            roster = buildRoster(tenantId);
        }
        Map<Object, Indictment> indictmentMap = indictmentUtils.getIndictmentMapForRoster(roster);

        for (Shift shift : shiftList) {
            Indictment indictment = indictmentMap.get(shift);
            if (shift.getEmployee() != null) {
                employeeIdToShiftViewListMap.computeIfAbsent(shift.getEmployee().getId(),
                                                             k -> new ArrayList<>())
                        .add(indictmentUtils.getShiftViewWithIndictment(timeZone, shift, indictment));
            } else {
                unassignedShiftViewList.add(indictmentUtils.getShiftViewWithIndictment(timeZone, shift, indictment));
            }
        }
        availabilityRosterView.setEmployeeIdToShiftViewListMap(employeeIdToShiftViewListMap);
        availabilityRosterView.setUnassignedShiftViewList(unassignedShiftViewList);
        Map<Long, List<EmployeeAvailabilityView>> employeeIdToAvailabilityViewListMap = new LinkedHashMap<>(
                employeeList.size());
        List<EmployeeAvailability> employeeAvailabilityList =
                employeeAvailabilityRepository.filterWithEmployee(tenantId, employeeSet,
                                                                  startDate.atStartOfDay(timeZone).toOffsetDateTime(),
                                                                  endDate.atStartOfDay(timeZone).toOffsetDateTime());

        for (EmployeeAvailability employeeAvailability : employeeAvailabilityList) {
            employeeIdToAvailabilityViewListMap.computeIfAbsent(employeeAvailability.getEmployee().getId(),
                                                                k -> new ArrayList<>())
                    .add(new EmployeeAvailabilityView(timeZone, employeeAvailability));
        }
        availabilityRosterView.setEmployeeIdToAvailabilityViewListMap(employeeIdToAvailabilityViewListMap);

        // TODO FIXME race condition solverManager's bestSolution might differ from the one we just fetched so the
        //  score might be inaccurate.
        availabilityRosterView.setScore(roster.getScore());
        availabilityRosterView.setRosterState(getRosterState(tenantId));
        availabilityRosterView.setIndictmentSummary(indictmentUtils.getIndictmentSummaryForRoster(roster));

        return availabilityRosterView;
    }

    // ************************************************************************
    // Roster
    // ************************************************************************

    @Transactional
    public Roster buildRoster(Integer tenantId) {
        ZoneId zoneId = getRosterState(tenantId).getTimeZone();
        List<Skill> skillList = skillRepository.findAllByTenantId(tenantId);
        List<Spot> spotList = spotRepository.findAllByTenantId(tenantId, PageRequest.of(0, Integer.MAX_VALUE));
        List<Employee> employeeList = employeeRepository.findAllByTenantId(tenantId, PageRequest.of(0,
                                                                                                    Integer.MAX_VALUE));
        List<EmployeeAvailability> employeeAvailabilityList = employeeAvailabilityRepository.findAllByTenantId(tenantId)
                .stream()
                .map(ea -> ea.inTimeZone(zoneId))
                .collect(Collectors.toList());

        List<Shift> shiftList = shiftRepository.findAllByTenantId(tenantId)
                .stream()
                .map(s -> s.inTimeZone(zoneId))
                .collect(Collectors.toList());

        Roster roster = new Roster((long) tenantId, tenantId, rosterConstraintConfigurationRepository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No RosterConstraintConfiguration entity found with tenantId(" + tenantId + ").")),
                                   skillList, spotList, employeeList, employeeAvailabilityList,
                                   getRosterState(tenantId), shiftList);

        ScoreDirector<Roster> scoreDirector = solverManager.getScoreDirector();
        scoreDirector.setWorkingSolution(roster);
        roster.setScore((HardMediumSoftLongScore) scoreDirector.calculateScore());
        return roster;
    }

    @Transactional
    public void updateShiftsOfRoster(Roster newRoster) {
        Integer tenantId = newRoster.getTenantId();
        // TODO HACK avoids optimistic locking exception while solve(), but it circumvents optimistic locking completely
        Map<Long, Employee> employeeIdMap = employeeRepository.findAllByTenantId(tenantId,
                                                                                 PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        Map<Long, Shift> shiftIdMap = shiftRepository.findAllByTenantId(tenantId)
                .stream()
                .collect(Collectors.toMap(Shift::getId, Function.identity()));

        for (Shift shift : newRoster.getShiftList()) {
            Shift attachedShift = shiftIdMap.get(shift.getId());
            if (attachedShift == null) {
                continue;
            }
            attachedShift.setEmployee((shift.getEmployee() == null) ?
                                              null : employeeIdMap.get(shift.getEmployee().getId()));
        }
    }

    // ************************************************************************
    // Solver
    // ************************************************************************

    public void solveRoster(Integer tenantId) {
        solverManager.solve(tenantId);
    }

    public void replanRoster(Integer tenantId) {
        solverManager.replan(tenantId);
    }

    public SolverStatus getSolverStatus(Integer tenantId) {
        return solverManager.getSolverStatus(tenantId);
    }

    public void terminateRosterEarly(Integer tenantId) {
        solverManager.terminate(tenantId);
    }

    // ************************************************************************
    // Publish
    // ************************************************************************
    @Transactional
    public void provision(Integer tenantId, Integer startRotationOffset, LocalDate fromDate,
                          LocalDate toDate, List<Long> timeBucketIdList) {
        RosterState rosterState = getRosterState(tenantId);
        List<TimeBucket> timeBucketList = timeBucketRepository.findAllById(timeBucketIdList);
        if (timeBucketList.stream().anyMatch(tb -> !tb.getTenantId().equals(tenantId))) {
            throw new IllegalArgumentException("Can only provision shifts from timebuckets from the same tenant");
        }
        if (startRotationOffset > rosterState.getRotationLength()) {
            throw new IllegalArgumentException("startRotationOffset (" + startRotationOffset
                                                       + ") is greater than the rotation length ("
                                                       + rosterState.getRotationLength() + ")");
        }
        if (startRotationOffset < 0) {
            throw new IllegalArgumentException("startRotationOffset (" + startRotationOffset
                                                       + ") is negative");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate (" + toDate.toString() + ") is before fromDate ("
                                                       + fromDate.toString() + ")");
        }

        int dayOffset = startRotationOffset;
        LocalDate shiftDate = fromDate;
        while (!shiftDate.isAfter(toDate)) {
            for (TimeBucket timeBucket : timeBucketList) {
                timeBucket.createShiftForOffset(shiftDate, dayOffset,
                                                rosterState.getTimeZone(), false)
                        .ifPresent(shiftRepository::save);
            }
            shiftDate = shiftDate.plusDays(1);
            dayOffset = (dayOffset + 1) % rosterState.getRotationLength();
        }
    }

    @Transactional
    public PublishResult publishAndProvision(Integer tenantId) {
        RosterState rosterState = getRosterState(tenantId);
        LocalDate publishFrom = rosterState.getFirstDraftDate();
        LocalDate publishTo = publishFrom.plusDays(rosterState.getPublishLength());
        LocalDate firstUnplannedDate = rosterState.getFirstUnplannedDate();

        // Publish
        ZoneId timeZone = rosterState.getTimeZone();
        List<Shift> publishedShifts = shiftRepository
                .findAllByTenantIdBetweenDates(tenantId,
                                               publishFrom.atStartOfDay(timeZone).toOffsetDateTime(),
                                               publishTo.atStartOfDay(timeZone).toOffsetDateTime());
        publishedShifts.forEach(s -> s.setOriginalEmployee(s.getEmployee()));
        shiftRepository.saveAll(publishedShifts);
        rosterState.setFirstDraftDate(publishTo);

        // Provision
        provision(tenantId, rosterState.getUnplannedRotationOffset(), firstUnplannedDate,
                  firstUnplannedDate.plusDays(rosterState.getPublishLength() - 1),
                  timeBucketRepository.findAllByTenantId(tenantId).stream()
                          .map(TimeBucket::getId).collect(Collectors.toList()));

        return new PublishResult(publishFrom, publishTo);
    }

    @Transactional
    public void commitChanges(Integer tenantId) {
        RosterState rosterState = getRosterState(tenantId);
        LocalDate publishFrom = LocalDate.now();
        LocalDate publishTo = rosterState.getFirstDraftDate();

        // Publish
        ZoneId timeZone = rosterState.getTimeZone();
        List<Shift> publishedShifts = shiftRepository
                .findAllByTenantIdBetweenDates(tenantId,
                                               publishFrom.atStartOfDay(timeZone).toOffsetDateTime(),
                                               publishTo.atStartOfDay(timeZone).toOffsetDateTime());
        publishedShifts.forEach(s -> s.setOriginalEmployee(s.getEmployee()));
        shiftRepository.saveAll(publishedShifts);
    }
}
