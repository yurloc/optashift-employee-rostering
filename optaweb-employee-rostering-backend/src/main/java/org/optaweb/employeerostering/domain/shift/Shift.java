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

package org.optaweb.employeerostering.domain.shift;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.entity.PlanningPin;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaweb.employeerostering.domain.common.AbstractPersistable;
import org.optaweb.employeerostering.domain.employee.Employee;
import org.optaweb.employeerostering.domain.shift.view.ShiftView;
import org.optaweb.employeerostering.domain.spot.Spot;

@Entity
@PlanningEntity(movableEntitySelectionFilter = MovableShiftFilter.class)
public class Shift extends AbstractPersistable {

    @ManyToOne
    private Employee rotationEmployee;

    @NotNull
    @ManyToOne
    private Spot spot;

    @NotNull
    private OffsetDateTime startDateTime;
    @NotNull
    private OffsetDateTime endDateTime;

    @PlanningPin
    private boolean pinnedByUser = false;

    @ManyToOne
    @PlanningVariable(valueRangeProviderRefs = "employeeRange", nullable = true)
    private Employee employee = null;

    @SuppressWarnings("unused")
    public Shift() {
    }

    public Shift(Integer tenantId, Spot spot, OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
        this(tenantId, spot, startDateTime, endDateTime, null);
    }

    public Shift(Integer tenantId, Spot spot, OffsetDateTime startDateTime, OffsetDateTime endDateTime,
                 Employee rotationEmployee) {
        super(tenantId);
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.spot = spot;
        this.rotationEmployee = rotationEmployee;
    }

    public Shift(ZoneId zoneId, ShiftView shiftView, Spot spot) {
        this(zoneId, shiftView, spot, null);
    }

    public Shift(ZoneId zoneId, ShiftView shiftView, Spot spot, Employee rotationEmployee) {
        super(shiftView);
        this.startDateTime = OffsetDateTime.of(shiftView.getStartDateTime(),
                                               zoneId.getRules().getOffset(shiftView.getStartDateTime()));
        this.endDateTime = OffsetDateTime.of(shiftView.getEndDateTime(),
                                             zoneId.getRules().getOffset(shiftView.getEndDateTime()));
        this.spot = spot;
        this.pinnedByUser = shiftView.isPinnedByUser();
        this.rotationEmployee = rotationEmployee;
    }

    @Override
    public String toString() {
        return spot + " " + startDateTime + "-" + endDateTime;
    }

    // ************************************************************************
    // Simple getters and setters
    // ************************************************************************

    public Spot getSpot() {
        return spot;
    }

    public void setSpot(Spot spot) {
        this.spot = spot;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(OffsetDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public OffsetDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(OffsetDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public boolean isPinnedByUser() {
        return pinnedByUser;
    }

    public void setPinnedByUser(boolean lockedByUser) {
        this.pinnedByUser = lockedByUser;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Employee getRotationEmployee() {
        return rotationEmployee;
    }

    public void setRotationEmployee(Employee rotationEmployee) {
        this.rotationEmployee = rotationEmployee;
    }

    public Shift inTimeZone(ZoneId zoneId) {
        Shift out = new Shift(zoneId, new ShiftView(zoneId, this), getSpot(), getRotationEmployee());
        out.setEmployee(getEmployee());
        return out;
    }
}
