import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.List;

/**
 * Routing Table class
 * @author omkar sarde
 * @author sharwari salunkhe
 */
public class Table {
    protected int id;
    protected List<Entry> podTable;

    Table(int p_id) {
        id = p_id;
        podTable = new ArrayList<>();
    }

    //overloading constructor
    Table() {
        id = 0;
        podTable = new ArrayList<>();
    }

    /**
     * Add Entry to routing table
     * @param in_id id of message
     * @param hopAddress hop of message
     */
    void addEntry(int in_id, String hopAddress) {
        //ignore if id is same as self id
        if (in_id != id) {
            //not present
            if (retrieveEntryFromIp(new String("10.0." + in_id + ".0")) == -999) {
                //cost mask inAddress hopAddress
                Entry entry = new Entry((byte) 1, (byte) 24, new String("10.0." + in_id + ".0"), hopAddress);
                podTable.add(entry);
                //display
                printDefaultTable();
            } else {
                //exists so just change
                int existingEntryIndx = retrieveEntryFromIp(new String("10.0." + in_id + ".0"));
                Entry currentEntry = podTable.get(existingEntryIndx);
                if (currentEntry.cost != 1) {
                    currentEntry.cost = 1;
                    currentEntry.hop = hopAddress;
                    podTable.set(existingEntryIndx, currentEntry);
                    //display
                    printDefaultTable();
                }
            }
        }
    }

    /**
     * Populate the routing table from the contents received from datagramPacket
     * @param datagramPacket received packet
     */
    void populateTable(DatagramPacket datagramPacket) {
        DatagramPacket incomingPacket = datagramPacket;
        byte[] buffer = new byte[incomingPacket.getLength()];
        System.arraycopy(incomingPacket.getData(), 0, buffer, 0, buffer.length);
        //ignoring command and version
        int in_id = buffer[3];
        //create return table
        id = in_id;
        int counter = 4;
        while (counter < buffer.length) {
            byte[] addressFamily = new byte[2], routeTag = new byte[2];
            //counter inc 2
            addressFamily[0] = buffer[counter++];
            addressFamily[1] = buffer[counter++];
            //counter inc 2
            routeTag[0] = buffer[counter++];
            routeTag[1] = buffer[counter++];
            //handling ip
            //counter inc 4
            int p1, p2, p3, p4;
            p1 = Byte.toUnsignedInt(buffer[counter++]);
            p2 = Byte.toUnsignedInt(buffer[counter++]);
            p3 = Byte.toUnsignedInt(buffer[counter++]);
            p4 = Byte.toUnsignedInt(buffer[counter++]);
            String ip = new String(p1 + "." + p2 + "." + p3 + "." + p4);
            counter += 3;
            byte subnetMask = buffer[counter++];
            //handling hop
            //counter inc4
            p1 = Byte.toUnsignedInt(buffer[counter++]);
            p2 = Byte.toUnsignedInt(buffer[counter++]);
            p3 = Byte.toUnsignedInt(buffer[counter++]);
            p4 = Byte.toUnsignedInt(buffer[counter++]);
            String hop = new String(p1 + "." + p2 + "." + p3 + "." + p4);
            counter += 3;
            byte cost = buffer[counter];
            podTable.add(new Entry(cost, subnetMask, ip, hop));
        }
            System.out.println("Open to receving Entries from: " + datagramPacket.getAddress().getHostAddress() );
            printTable();
    }

    /**
     * Update routing table based on earlier table and sender address
     * @param in_table
     * @param senderAddress
     * @return true if update success full
     */
    boolean updateSelfTable(Table in_table, String senderAddress){
        try {
            boolean updateFlag = false;
            for (Entry entry:in_table.podTable){
                String ipAddress = entry.address;
                int pod_id = Integer.parseInt(ipAddress.split("\\.")[2]);
                Entry retrivedEntry = retrieveEntryFromip(ipAddress);
                if(id != pod_id){
                    byte updatedCost = (byte) (entry.cost+1);
                    //set to infinity
                    if (updatedCost > 16){
                        updatedCost = 16;
                    }
                    if(retrivedEntry == null){
                        String localAddress = "10.0."+pod_id+".0";
                        retrivedEntry = new Entry(updatedCost,(byte)24,localAddress,senderAddress);
                        podTable.add(retrivedEntry);
                        updateFlag = true;
                        continue;
                    }
                    if(updatedCost < getCost(retrivedEntry)){
                        retrivedEntry.hop = senderAddress;
                        retrivedEntry.cost = updatedCost;
                        updateFlag = true;
                    }else {
                        if(retrivedEntry.hop.equals(senderAddress)){
                            if(retrivedEntry.cost != updatedCost){
                                retrivedEntry.cost = updatedCost;
                                updateFlag = true;
                            }
                        }
                    }
                }
            }
            return updateFlag;
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Retrieve position of entry in table
     * @param address ipAddress of Entry
     * @return position of entry in table
     */
    int retrieveEntryFromIp(String address) {
        for (int i = 0; i < podTable.size(); i++) {
            Entry currentEntry = podTable.get(i);
            if (address.equals(currentEntry.address)) {
                return i;
            }
        }
        return -999;
    }

    /**
     * Retrieve entry from table based on destination address
     * @param destination destination address
     * @return table entry
     */
    Entry retrieveEntryFromDestinationIp(String destination){
        try {
            System.out.println("Searching hop for: " + destination);
            Entry entry = retrieveEntryFromip(destination);
            int counter = 0;
            while (entry == null || entry.cost == 16) {
                if (entry != null) {
                    System.out.println("Cant send message to: " + destination + " retrying");
                } else {
                    System.out.println("Cant find entry for: " + destination + " retrying");
                }
                Thread.sleep(5000);
                entry = retrieveEntryFromip(destination);
                counter++;
                if(10<=counter){
                    System.out.println("Passed max retries, skipping");
                    return null;
                }
            }
            return entry;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Retrieve entry from table based on source ip
     * @param address source ip address
     * @return table entry
     */
    Entry retrieveEntryFromip(String address) {
        for (int i = 0; i < podTable.size(); i++) {
            Entry currentEntry = podTable.get(i);
            if (address.equals(currentEntry.address)) {
                return currentEntry;
            }
        }
        return null;
    }

    /**
     * Retrieve message cost, infinity if entry does not exist
     * @param entry Entry in table
     * @return cost
     */
    int getCost(Entry entry){
        //infinity
        return entry != null?entry.cost : (byte)16;
    }

    /**
     * Find entries for same ip address
     * @param ipAddress ipaddress
     * @return entries in table format
     */
    Table getEntriesForSameIp(String ipAddress) {
        Table returnTable = new Table();
        for (Entry entry : podTable) {
            if (ipAddress.equals(entry.hop)) {
                returnTable.podTable.add(entry);
            }
        }
        return returnTable;
    }

    /**
     * Set cost of all entries to infinity
     */
    void setCostToInfinity() {
        for (Entry entry : podTable) {
            entry.cost = 16;
        }
    }

    /**
     * Print current table
     */
    void printTable() {
        if(podTable.size()>0) {
            System.out.println("TABLE");
            for (Entry e : podTable) {
                System.out.println(e);
            }
        }
    }

    /**
     * Print default table
     */
    void printDefaultTable(){
        System.out.println("Routing Table");
        for(Entry entry : podTable){
            String display ="Entry{Address="+entry.address+'\''+",mask=24"+", hopTo="+entry.hop
                    +'\''+", cost="+entry.cost+"}";
            System.out.println(display);
        }
        System.out.println();
    }

    /**
     * Helper class for routing table
     */
    class Entry {
        byte cost, mask;
        String address, hop;

        /**
         * Create routing table entry
         * @param in_cost cost of entry
         * @param in_mask subnet mask
         * @param in_address sourceAddress
         * @param in_hop    nextHop address
         */
        Entry(byte in_cost, byte in_mask, String in_address, String in_hop) {
            cost = in_cost;
            mask = in_mask;
            address = in_address;
            hop = in_hop;
        }

        @Override
        public String toString() {
            return "Entry{" +
                    "Address='" + address + '\'' +
                    ", mask=" + mask + '\'' +
                    ", hopTo='" + hop + '\'' +
                    ", cost=" + cost +
                    '}';
        }
    }
}


