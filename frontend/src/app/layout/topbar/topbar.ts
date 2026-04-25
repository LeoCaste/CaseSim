import { Component } from '@angular/core';
import { UserContext } from '../../core/services/user-context';

@Component({
  selector: 'app-topbar',
  imports: [],
  templateUrl: './topbar.html',
  styleUrl: './topbar.css'
})
export class Topbar {
  constructor(public userContext: UserContext) {}
}
