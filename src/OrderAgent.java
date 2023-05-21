import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class OrderAgent extends Agent {
    // The place which we have to visit
    private String targetPlace;
    // The list of known resources agents
    private AID[] resourceAgents;

    //Agent initializations
    protected void setup() {
        // Printout a welcome message
        System.out.println("Order-agent "+getAID().getName()+" is ready.");

        // Get the name of the place to visit as a start-up argument
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            targetPlace = (String) args[0];
            System.out.println("Target place is "+ targetPlace);

            // Add a TickerBehaviour that schedules a request to resource agents every minute
            addBehaviour(new TickerBehaviour(this, 60000) {
                protected void onTick() {
                    System.out.println("Trying to visit "+ targetPlace);
                    // Update the list of resource agents
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("place-visiting");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Found the following resource agents:");
                        resourceAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            resourceAgents[i] = result[i].getName();
                            System.out.println(resourceAgents[i].getName());
                        }
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    // Perform the request
                    myAgent.addBehaviour(new RequestPerformer());
                }
            } );
        }
        else {
            // Make the agent terminate
            System.out.println("No target place to visit");
            doDelete();
        }
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        // Printout a dismissal message
        System.out.println("Order-agent "+getAID().getName()+" terminating.");
    }

    /**
     Inner class RequestPerformer.
     This is the behaviour used by Order-buyer agents to request resource
     agents the target place.
     */
    private class RequestPerformer extends Behaviour {
        private AID bestResource; // The agent who provides the best offer
        private int bestPrice;  // The best offered price
        private int repliesCnt = 0; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Send the cfp to all resources
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < resourceAgents.length; ++i) {
                        cfp.addReceiver(resourceAgents[i]);
                    }
                    cfp.setContent(targetPlace);
                    cfp.setConversationId("place-trade");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("place-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Receive all proposals/refusals from resources agents
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // This is an offer
                            int price = Integer.parseInt(reply.getContent());
                            if (bestResource == null || price < bestPrice) {
                                // This is the best offer at present
                                bestPrice = price;
                                bestResource = reply.getSender();
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= resourceAgents.length) {
                            // We received all replies
                            step = 2;
                        }
                    }
                    else {
                        block();
                    }
                    break;
                case 2:

                    //Send the order to the resource that provided the best offer
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestResource);
                    order.setContent(targetPlace);
                    order.setConversationId("place-trade");
                    order.setReplyWith("order"+System.currentTimeMillis());
                    myAgent.send(order);

                    // Prepare the template to get the place order reply
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("place-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    // Receive the place order reply
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Place order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Place visited successful. We can terminate
                            System.out.println(targetPlace +" successfully visited target place with agent "+reply.getSender().getName());
                            System.out.println("Price = "+bestPrice);
                            myAgent.doDelete();
                        }
                        else {
                            System.out.println("Attempt failed: someone visiting that place.");
                        }

                        step = 4;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestResource == null) {
                System.out.println("Attempt failed: "+ targetPlace +" not available for visiting");
            }
            return ((step == 2 && bestResource == null) || step == 4);
        }
    }
}