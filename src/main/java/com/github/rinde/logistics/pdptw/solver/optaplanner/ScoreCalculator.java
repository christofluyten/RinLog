/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.solver.optaplanner;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.score.director.incremental.AbstractIncrementalScoreCalculator;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 *
 * @author Rinde van Lon
 */
public class ScoreCalculator
    extends AbstractIncrementalScoreCalculator<PDPSolution> {

  long hardScore;
  long softScore;

  Object2LongMap<Visit> doneTimes;
  Object2LongMap<Visit> travelTimes;
  Object2LongMap<Visit> tardiness;

  Map<Parcel, Vehicle> pickupOwner;
  Map<Parcel, Vehicle> deliveryOwner;

  SetMultimap<Vehicle, ParcelVisit> changes;

  long startTime;

  Set<ParcelVisit> unplannedParcelVisits;

  @Override
  public void resetWorkingSolution(PDPSolution workingSolution) {
    System.out.println("resetWorkingSolution: " + workingSolution);

    unplannedParcelVisits = new LinkedHashSet<>(workingSolution.parcelList);

    changes = LinkedHashMultimap.create();

    startTime = workingSolution.startTime;

    final int numVisits = workingSolution.parcelList.size();
    final int numVehicles = workingSolution.vehicleList.size();
    final int size = numVisits + numVehicles;
    doneTimes = new Object2LongOpenHashMap<>(size);
    travelTimes = new Object2LongOpenHashMap<>(size);
    tardiness = new Object2LongOpenHashMap<>(size);

    pickupOwner = new LinkedHashMap<>(numVisits);
    deliveryOwner = new LinkedHashMap<>(numVisits);

    hardScore = 0;
    softScore = 0;
    for (final Vehicle v : workingSolution.vehicleList) {
      // final long currentTime = workingSolution.startTime;

      ParcelVisit pv = v.getNextVisit();
      // if (v.vehicle.getDestination().isPresent()) {
      // if (pv == null
      // || !pv.getParcel().equals(v.vehicle.getDestination().get())) {
      // hardScore -= 1L;
      // }
      // }

      // final Set<Parcel> deliveryRequired = new LinkedHashSet<>();
      // deliveryRequired.addAll(v.vehicle.getContents());

      while (pv != null) {
        insert(pv);
        pv = pv.getNextVisit();
      }

      updateDepotScore(v);

      // the number of parcels that are not delivered even though they should
      // have been is used as a hard constraint violation
      // hardScore -= deliveryRequired.size();
    }
  }

  void updateDepotScore(Vehicle v) {
    softScore += tardiness.getLong(v);
    softScore += travelTimes.getLong(v);

    final ParcelVisit lastStop = v.getLastVisit();
    final Point fromPos =
      lastStop == null ? v.getPosition() : lastStop.getPosition();
    long currentTime =
      lastStop == null ? startTime : doneTimes.getLong(lastStop);

    // travel to depot soft constraints
    final long depotTT =
      v.computeTravelTime(fromPos, v.getDepotLocation());
    currentTime += depotTT;
    softScore -= depotTT;
    travelTimes.put(v, depotTT);

    final long depotTardiness = v.computeDepotTardiness(currentTime);
    softScore -= depotTardiness;
    tardiness.put(v, depotTardiness);
  }

  @Override
  public void beforeEntityAdded(Object entity) {
    System.out.println("beforeEntityAdded: " + entity);

  }

  @Override
  public void afterEntityAdded(Object entity) {
    System.out.println("afterEntityAdded: " + entity);

  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    System.out.println("beforeVariableChanged: " + entity);
    System.out.println(" > next:" + ((Visit) entity).getNextVisit());

    final Visit visit = (Visit) entity;

    if (visit.getNextVisit() == null) {
      // we can safely ignore this

    } else {
      // we have to remove this entity from the schedule
      remove(visit.getNextVisit());
      changes.put(visit.getVehicle(), visit.getNextVisit());
    }
    System.out.println("softScore: " + softScore);
  }

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    System.out.println("afterVariableChanged: " + entity);
    System.out.println(" > next:" + ((Visit) entity).getNextVisit());

    final Visit visit = (Visit) entity;
    if (visit.getNextVisit() == null) {
      // we can ignore this
    } else {
      // we have to add this entity to the schedule

      insert(visit.getNextVisit());
      changes.put(visit.getVehicle(), visit.getNextVisit());
    }

    System.out.println("softScore: " + softScore);
  }

  @Override
  public void beforeEntityRemoved(Object entity) {
    System.out.println("beforeEntityRemoved: " + entity);

  }

  @Override
  public void afterEntityRemoved(Object entity) {
    System.out.println("afterEntityRemoved: " + entity);

  }

  @Override
  public Score calculateScore() {
    System.out.println("***calculate score***");
    if (!changes.isEmpty()) {
      for (final Entry<Vehicle, Collection<ParcelVisit>> entry : changes.asMap()
          .entrySet()) {
        updateRoute(entry.getKey(), entry.getValue());
      }
    }
    changes.clear();
    return HardSoftLongScore.valueOf(hardScore, softScore);
  }

  void updateRoute(Vehicle v, Collection<ParcelVisit> visits) {
    System.out.println("updateRoute " + v);
    ParcelVisit cur = v.getNextVisit();

    while (cur != null) {
      // we know that visits is always a set
      if (!visits.contains(cur)) {
        remove(cur);
      }
      insert(cur);

      cur = cur.getNextVisit();
    }

    updateDepotScore(v);
  }

  void remove(ParcelVisit pv) {

    softScore += travelTimes.getLong(pv);
    softScore += tardiness.getLong(pv);

  }

  void insert(ParcelVisit pv) {
    unplannedParcelVisits.remove(pv);

    final Vehicle vehicle = pv.getVehicle();
    final Visit prev = pv.getPreviousVisit();
    final Point prevPos = prev.getPosition();

    long currentTime;
    if (prev.equals(vehicle)) {
      currentTime = startTime;
    } else {
      currentTime = doneTimes.getLong(prev);
    }

    final Point newPos = pv.getPosition();

    // compute travel time from current pos to parcel pos
    final long tt = vehicle.computeTravelTime(prevPos, newPos);
    currentTime += tt;
    softScore -= tt;
    travelTimes.put(pv, tt);

    // compute tardiness
    currentTime = pv.computeServiceStartTime(currentTime);
    final long tard = pv.computeTardiness(currentTime);
    softScore -= tard;
    tardiness.put(pv, tard);

    // compute time when servicing of this parcel is done
    currentTime += pv.getServiceDuration();
    doneTimes.put(pv, currentTime);

    // check hard constraints
    // if (deliveryRequired.contains(pv.getParcel())) {
    // // it needs to be delivered
    // if (pv.getVisitType() == VisitType.DELIVER) {
    // deliveryRequired.remove(pv.getParcel());
    // } else {
    // hardScore -= 1L;
    // }
    // } else {
    // // it needs to be picked up
    // if (pv.getVisitType() == VisitType.PICKUP) {
    // deliveryRequired.add(pv.getParcel());
    // } else {
    // hardScore -= 1L;
    // }
    // }
  }

}