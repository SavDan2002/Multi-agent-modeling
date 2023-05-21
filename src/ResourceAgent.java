import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class ResourceAgent extends Agent {
    // The list of places to visit (maps the names of places to its price)
    private Hashtable list;
    // The GUI by means of which the user can add places in the list
    private ResourceGui myGui;

    // Agent initializations
    protected void setup() {
        // Create the list
        list = new Hashtable();

        // Create and show the GUI
        myGui = new ResourceGui(this);
        myGui.showGui();

        // Register the place-selling service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("place-visiting");
        sd.setName("JADE-place-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Add the behaviour serving queries from order agents
        addBehaviour(new OfferRequestsServer());

        // Add the behaviour serving purchase orders from order agents
        addBehaviour(new PurchaseOrdersServer());
    }

    //agent clean-up operations
    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Close the GUI
        myGui.dispose();
        // Printout a dismissal message
        System.out.println("Resource-agent "+getAID().getName()+" terminating.");
    }

    /**
     This is invoked by the GUI when the user adds a new place to visit
     */
    public void updateCatalogue(final String place, final int price) {
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                list.put(place, price);
                System.out.println(place+" inserted into list. Price = "+price);
            }
        } );
    }

    /**
     Inner class OfferRequestsServer.
     This is the behaviour used by resource agents to serve incoming requests
     for offer from order agents.
     If the requested book is in the local list the resource agent replies
     with a PROPOSE message specifying the price. Otherwise a REFUSE message is
     sent back.
     */
    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                String place = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = (Integer) list.get(place);
                if (price != null) {
                    // The requested place is available for visiting. Reply with the price
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(price.intValue()));
                }
                else {
                    // The requested place is not available for visiting.
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }  // End of inner class OfferRequestsServer

    /**
     Inner class PurchaseOrdersServer.
     This is the behaviour used by resource agents to serve incoming
     offer acceptances from order agents.
     The resource agent removes the purchased place from its list
     and replies with an INFORM message to notify the order agent that the
     purchase has been sucesfully completed.
     */
    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Message received. Process it
                String place = msg.getContent();
                ACLMessage reply = msg.createReply();

                Integer price = (Integer) list.remove(place);
                if (price != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(place+" sold to agent "+msg.getSender().getName());
                }
                else {
                    // The requested place has been sold to another buyer in the meanwhile .
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }
}
