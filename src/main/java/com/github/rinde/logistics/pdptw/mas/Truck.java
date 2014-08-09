/**
 * 
 */
package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.base.Objects.toStringHelper;

import java.util.LinkedList;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.SimulatorUser;
import com.github.rinde.rinsim.core.TimeLapse;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.fsm.StateMachine.StateMachineEvent;
import com.github.rinde.rinsim.fsm.StateMachine.StateTransitionEvent;
import com.github.rinde.rinsim.pdptw.common.DefaultParcel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.google.common.base.Optional;

/**
 * A vehicle entirely controlled by a {@link RoutePlanner} and a
 * {@link Communicator}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class Truck extends RouteFollowingVehicle implements Listener,
    SimulatorUser {

  private final RoutePlanner routePlanner;
  private final Communicator communicator;
  private boolean changed;

  /**
   * Create a new Truck using the specified {@link RoutePlanner} and
   * {@link Communicator}.
   * @param pDto The truck properties.
   * @param rp The route planner used.
   * @param c The communicator used.
   */
  public Truck(VehicleDTO pDto, RoutePlanner rp, Communicator c) {
    super(pDto, true);
    routePlanner = rp;
    communicator = c;
    communicator.addUpdateListener(this);
    stateMachine.getEventAPI().addListener(this,
        StateMachineEvent.STATE_TRANSITION);
  }

  @Override
  public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
    super.initRoadPDP(pRoadModel, pPdpModel);
    routePlanner.init(pRoadModel, pPdpModel, this);
    communicator.init(pRoadModel, pPdpModel, this);
  }

  @Override
  protected void preTick(TimeLapse time) {
    if (stateMachine.stateIs(waitState)) {
      if (changed) {
        updateAssignmentAndRoutePlanner();
        updateRoute();
      } else if (getRoute().isEmpty() && routePlanner.current().isPresent()) {
        updateRoute();
      }
    } else if (changed && isDiversionAllowed()
        && !stateMachine.stateIs(serviceState)) {
      updateAssignmentAndRoutePlanner();
      updateRoute();
    }
  }

  /**
   * Updates the {@link RoutePlanner} with the assignment of the
   * {@link Communicator}.
   */
  protected void updateAssignmentAndRoutePlanner() {
    changed = false;

    routePlanner.update(communicator.getParcels(), getCurrentTime().getTime());
    final Optional<DefaultParcel> cur = routePlanner.current();
    if (cur.isPresent()) {
      communicator.waitFor(cur.get());
    }
  }

  /**
   * Updates the route based on the {@link RoutePlanner}.
   */
  protected void updateRoute() {
    if (routePlanner.current().isPresent()) {
      setRoute(routePlanner.currentRoute().get());
    } else {
      setRoute(new LinkedList<DefaultParcel>());
    }
  }

  @Override
  public void handleEvent(Event e) {
    if (e.getEventType() == CommunicatorEventType.CHANGE) {
      changed = true;
    } else {
      // we know this is safe since it can only be one type of event
      @SuppressWarnings("unchecked")
      final StateTransitionEvent<StateEvent, RouteFollowingVehicle> event = (StateTransitionEvent<StateEvent, RouteFollowingVehicle>) e;

      // when diverting -> unclaim previous
      if ((event.trigger == DefaultEvent.REROUTE || event.trigger == DefaultEvent.NOGO)
          && !pdpModel.get().getParcelState(gotoState.getPreviousDestination())
              .isPickedUp()) {
        communicator.unclaim(gotoState.getPreviousDestination());
      }

      if (event.trigger == DefaultEvent.GOTO
          || event.trigger == DefaultEvent.REROUTE) {
        final DefaultParcel cur = getRoute().iterator().next();
        if (!pdpModel.get().getParcelState(cur).isPickedUp()) {
          communicator.claim(cur);
        }
      } else if (event.trigger == DefaultEvent.DONE) {
        communicator.done();
        routePlanner.next(getCurrentTime().getTime());
      }

      if ((event.newState == waitState || (isDiversionAllowed() && event.newState != serviceState))
          && changed) {
        updateAssignmentAndRoutePlanner();
        updateRoute();
      }
    }
  }

  /**
   * @return The {@link Communicator} of this {@link Truck}.
   */
  public Communicator getCommunicator() {
    return communicator;
  }

  /**
   * @return The {@link RoutePlanner} of this {@link Truck}.
   */
  public RoutePlanner getRoutePlanner() {
    return routePlanner;
  }

  @Override
  public void setSimulator(SimulatorAPI api) {
    api.register(communicator);
    api.register(routePlanner);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .addValue(Integer.toHexString(hashCode()))
        .add("rp", routePlanner)
        .add("c", communicator)
        .toString();
  }
}