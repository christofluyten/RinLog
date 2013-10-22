/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import rinde.logistics.pdptw.mas.TruckConfiguration;
import rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import rinde.sim.core.TickListener;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.AbstractModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.util.SupplierRng;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
@RunWith(Parameterized.class)
public class CommTest implements TickListener {
  protected final MASConfiguration configuration;
  protected DynamicPDPTWProblem problem;

  public CommTest(MASConfiguration c) {
    configuration = c;
  }

  @Test
  public void test() throws IOException {
    problem = ExperimentTest.init(Gendreau06Parser.parse(
        "files/scenarios/gendreau06/req_rapide_1_240_24", 10), configuration,
        123, false);

    problem.getSimulator().tick();
    problem.getSimulator().addTickListener(this);
    problem.simulate();
  }

  @Parameters
  public static Collection<Object[]> configs() {
    return asList(new Object[][] { /* */
        { new TruckConfiguration(RandomRoutePlanner.supplier(),
            RandomBidder.supplier(), ImmutableList.of(
                AuctionCommModel.supplier(), CommTestModel.supplier())) },
        { new TruckConfiguration(RandomRoutePlanner.supplier(),
            BlackboardUser.supplier(), ImmutableList.of(
                BlackboardCommModel.supplier(), CommTestModel.supplier())) },

    });
  }

  @Override
  public void tick(TimeLapse timeLapse) {

    final Optional<RoadModel> roadModel = Optional.fromNullable(problem
        .getSimulator().getModelProvider().getModel(RoadModel.class));

    final Optional<CommTestModel> commTestModel = Optional.fromNullable(problem
        .getSimulator().getModelProvider().getModel(CommTestModel.class));

    for (final Communicator c : commTestModel.get().communicators) {
      assertTrue(
          "The communicator may only return parcels which are not yet picked up",
          roadModel.get().getObjectsOfType(Parcel.class)
              .containsAll(c.getParcels()));
    }
  }

  @Override
  public void afterTick(TimeLapse timeLapse) {}

  protected static class CommTestModel extends AbstractModel<Communicator> {
    final List<Communicator> communicators;

    CommTestModel() {
      communicators = newArrayList();
    }

    @Override
    public boolean register(Communicator element) {
      communicators.add(element);
      return false;
    }

    @Override
    public boolean unregister(Communicator element) {
      throw new UnsupportedOperationException();
    }

    public static SupplierRng<CommTestModel> supplier() {
      return new SupplierRng<CommTestModel>() {
        @Override
        public CommTestModel get(long seed) {
          return new CommTestModel();
        }
      };
    }
  }
}
