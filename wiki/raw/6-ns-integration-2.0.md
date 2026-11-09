### nextcloud integration 2.0

*One side directed sharing Pnone->Server is used for tracking purchases*

#### Phone client side
if nextcloud credentials and address is set:
- User can send list to nextcloud. List is sent with its id to avoid duplicates.
- Phone sends some JSON file to nextcloud. just text with list.
- Phone client contains no mapping logic.
- nextcloud connection logic can be taken from earlier code. search nor nextcloud.

#### Server side
- Server recieves json from client and stores in in come ingoing list
- User starts mapping process.
  - it is possible to map categories, stores, products.
  - List is saved with the same id from client
  - if AI API is set, AI mapping is available.
  - ai mapping can map automaticalu categories, stores, products
  - user reviews mapped and created items and approves it.
 
*Json format should be shared between both applications*
